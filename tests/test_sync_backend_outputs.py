import importlib.util
import unittest
from collections.abc import Mapping
from pathlib import Path


SCRIPT_PATH = Path(__file__).parents[1] / "scripts" / "sync-backend-outputs.py"
SPEC = importlib.util.spec_from_file_location("sync_backend_outputs", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Não foi possível carregar sync-backend-outputs.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class RecordingHcpClient:
    def __init__(self) -> None:
        self.calls: list[
            tuple[str, str, dict[str, tuple[str, bool]]]
        ] = []

    def set_workspace_variables(
        self,
        organization: str,
        workspace_name: str,
        definitions: dict[str, tuple[str, bool]],
    ) -> None:
        self.calls.append((organization, workspace_name, definitions))


class RecordingGitHubClient:
    def __init__(self) -> None:
        self.calls: list[tuple[str, str, str, str]] = []

    def set_environment_variable(
        self, repository: str, environment: str, name: str, value: str
    ) -> None:
        self.calls.append((repository, environment, name, value))


class RecordingHcpApiClient(MODULE.HcpClient):
    def __init__(self, variables: list[Mapping[str, object]]) -> None:
        super().__init__("token")
        self.variables = variables
        self.requests: list[tuple[str, str, Mapping[str, object] | None]] = []

    def _request(
        self, method: str, path: str, payload: Mapping[str, object] | None = None
    ) -> Mapping[str, object]:
        self.requests.append((method, path, payload))
        if path.startswith("organizations/"):
            return {"data": {"id": "ws-123"}}
        if method == "GET" and "/vars" in path:
            return {"data": self.variables}
        return {}


class RecordingGitHubApiClient(MODULE.GitHubClient):
    def __init__(self, existing: bool) -> None:
        super().__init__("token")
        self.existing = existing
        self.requests: list[
            tuple[str, str, Mapping[str, object] | None, bool]
        ] = []

    def _request(
        self,
        method: str,
        path: str,
        payload: Mapping[str, object] | None = None,
        *,
        allow_not_found: bool = False,
    ) -> Mapping[str, object] | None:
        self.requests.append((method, path, payload, allow_not_found))
        if method == "GET":
            return {} if self.existing else None
        return {}


class SyncBackendOutputsTest(unittest.TestCase):
    def test_syncs_auth_newrelic_and_github_environments(self) -> None:
        hcp_client = RecordingHcpClient()
        github_client = RecordingGitHubClient()

        MODULE.sync_outputs(
            hcp_client,
            github_client,
            "http://backend.example.com/",
            "oficina-org",
            "oficina-auth-homolog",
            "oficina-newrelic-homolog",
            "tiagomiele/fiap-tc-fase4-oficina-os-service",
            "tiagomiele/fiap-tc-fase4-oficina-kubernetes-infra",
            "homolog",
        )

        self.assertEqual(
            hcp_client.calls,
            [
                (
                    "oficina-org",
                    "oficina-auth-homolog",
                    {"backend_base_url": ("http://backend.example.com", False)},
                ),
                (
                    "oficina-org",
                    "oficina-newrelic-homolog",
                    {
                        "health_check_url": (
                            "http://backend.example.com/actuator/health",
                            False,
                        ),
                        "synthetic_monitor_enabled": ("true", True),
                    },
                ),
            ],
        )
        self.assertEqual(
            github_client.calls,
            [
                (
                    "tiagomiele/fiap-tc-fase4-oficina-os-service",
                    "homolog",
                    "BACKEND_BASE_URL",
                    "http://backend.example.com",
                ),
                (
                    "tiagomiele/fiap-tc-fase4-oficina-kubernetes-infra",
                    "homolog",
                    "HEALTH_CHECK_URL",
                    "http://backend.example.com/actuator/health",
                ),
                (
                    "tiagomiele/fiap-tc-fase4-oficina-kubernetes-infra",
                    "homolog",
                    "SYNTHETIC_MONITOR_ENABLED",
                    "true",
                ),
            ],
        )

    def test_validates_and_normalizes_backend_url(self) -> None:
        self.assertEqual(
            MODULE.normalized_backend_url("https://backend.example.com/"),
            "https://backend.example.com",
        )
        for invalid in (
            "backend.example.com",
            "ftp://backend.example.com",
            "http://user:password@backend.example.com",
            "http://backend.example.com/path",
            "http://backend.example.com?query=value",
        ):
            with self.subTest(invalid=invalid):
                with self.assertRaisesRegex(MODULE.SyncError, "URL pública"):
                    MODULE.normalized_backend_url(invalid)

    def test_hcp_upserts_variables_and_removes_duplicates(self) -> None:
        client = RecordingHcpApiClient(
            [
                {
                    "id": "var-primary",
                    "attributes": {
                        "key": "backend_base_url",
                        "category": "terraform",
                    },
                },
                {
                    "id": "var-duplicate",
                    "attributes": {
                        "key": "backend_base_url",
                        "category": "terraform",
                    },
                },
            ]
        )

        client.set_workspace_variables(
            "oficina-org",
            "oficina-auth-homolog",
            {"backend_base_url": ("http://backend.example.com", False)},
        )

        operations = [(method, path) for method, path, _ in client.requests]
        self.assertIn(("PATCH", "workspaces/ws-123/vars/var-primary"), operations)
        self.assertIn(("DELETE", "workspaces/ws-123/vars/var-duplicate"), operations)

    def test_hcp_creates_missing_variable(self) -> None:
        client = RecordingHcpApiClient([])

        client.set_workspace_variables(
            "oficina-org",
            "oficina-newrelic-homolog",
            {"synthetic_monitor_enabled": ("true", True)},
        )

        self.assertIn(
            ("POST", "workspaces/ws-123/vars"),
            [(method, path) for method, path, _ in client.requests],
        )
        payload = next(
            payload
            for method, _, payload in client.requests
            if method == "POST"
        )
        self.assertTrue(payload["data"]["attributes"]["hcl"])

    def test_github_creates_or_updates_environment_variable(self) -> None:
        create_client = RecordingGitHubApiClient(existing=False)
        create_client.set_environment_variable(
            "owner/backend", "homolog", "BACKEND_BASE_URL", "http://backend"
        )
        self.assertEqual(
            [request[0] for request in create_client.requests], ["GET", "POST"]
        )

        update_client = RecordingGitHubApiClient(existing=True)
        update_client.set_environment_variable(
            "owner/backend", "homolog", "BACKEND_BASE_URL", "http://backend"
        )
        self.assertEqual(
            [request[0] for request in update_client.requests], ["GET", "PATCH"]
        )


if __name__ == "__main__":
    unittest.main()
