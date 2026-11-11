#!/usr/bin/env python3

import argparse
import json
import os
import sys
from collections.abc import Mapping
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlparse
from urllib.request import Request, urlopen

HCP_API_BASE_URL = "https://app.terraform.io/api/v2"
GITHUB_API_BASE_URL = "https://api.github.com"
DESCRIPTION = "Gerenciada por scripts/sync-backend-outputs.py"


class SyncError(RuntimeError):
    pass


class HcpClient:
    def __init__(self, token: str) -> None:
        if not token.strip():
            raise SyncError("TF_API_TOKEN não configurado.")
        self._token = token

    def _request(
        self, method: str, path: str, payload: Mapping[str, object] | None = None
    ) -> Mapping[str, object]:
        body = None if payload is None else json.dumps(payload).encode("utf-8")
        request = Request(
            f"{HCP_API_BASE_URL}/{path}",
            data=body,
            method=method,
            headers={
                "Authorization": f"Bearer {self._token}",
                "Content-Type": "application/vnd.api+json",
            },
        )
        try:
            with urlopen(request, timeout=30) as response:
                content = response.read()
        except HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            raise SyncError(
                f"HCP Terraform retornou HTTP {error.code} em {method} {path}: {detail}"
            ) from error
        except URLError as error:
            raise SyncError(
                f"Falha de conexão com o HCP Terraform em {method} {path}: {error.reason}"
            ) from error

        if not content:
            return {}
        result = json.loads(content)
        if not isinstance(result, dict):
            raise SyncError(f"Resposta inválida do HCP Terraform em {method} {path}.")
        return result

    def workspace_id(self, organization: str, workspace_name: str) -> str:
        response = self._request(
            "GET",
            "organizations/"
            f"{quote(organization, safe='')}/workspaces/{quote(workspace_name, safe='')}",
        )
        data = response.get("data")
        if not isinstance(data, dict) or not isinstance(data.get("id"), str):
            raise SyncError(f"Workspace HCP não encontrado: {workspace_name}.")
        return data["id"]

    def workspace_variables(self, workspace_id: str) -> list[Mapping[str, object]]:
        response = self._request(
            "GET", f"workspaces/{quote(workspace_id, safe='')}/vars?page%5Bsize%5D=100"
        )
        data = response.get("data")
        if not isinstance(data, list):
            raise SyncError(f"Lista de variáveis inválida para o workspace {workspace_id}.")
        return [item for item in data if isinstance(item, dict)]

    def set_workspace_variables(
        self,
        organization: str,
        workspace_name: str,
        definitions: Mapping[str, tuple[str, bool]],
    ) -> None:
        workspace_id = self.workspace_id(organization, workspace_name)
        variables = self.workspace_variables(workspace_id)

        for key, (value, hcl) in definitions.items():
            matches = [
                variable
                for variable in variables
                if _variable_attribute(variable, "key") == key
                and _variable_attribute(variable, "category") == "terraform"
            ]
            payload: dict[str, object] = {
                "data": {
                    "type": "vars",
                    "attributes": {
                        "key": key,
                        "value": value,
                        "description": DESCRIPTION,
                        "category": "terraform",
                        "hcl": hcl,
                        "sensitive": False,
                    },
                }
            }

            if not matches:
                self._request("POST", f"workspaces/{workspace_id}/vars", payload)
                continue

            variable_id = matches[0].get("id")
            if not isinstance(variable_id, str):
                raise SyncError(
                    f"Variável {key} sem identificador no workspace {workspace_name}."
                )
            data = payload["data"]
            if not isinstance(data, dict):
                raise SyncError("Payload interno inválido.")
            data["id"] = variable_id
            self._request(
                "PATCH", f"workspaces/{workspace_id}/vars/{variable_id}", payload
            )

            for duplicate in matches[1:]:
                duplicate_id = duplicate.get("id")
                if isinstance(duplicate_id, str):
                    self._request(
                        "DELETE", f"workspaces/{workspace_id}/vars/{duplicate_id}"
                    )


class GitHubClient:
    def __init__(self, token: str) -> None:
        if not token.strip():
            raise SyncError("Secret REPO_SYNC_TOKEN não configurado.")
        self._token = token

    def _request(
        self,
        method: str,
        path: str,
        payload: Mapping[str, object] | None = None,
        *,
        allow_not_found: bool = False,
    ) -> Mapping[str, object] | None:
        body = None if payload is None else json.dumps(payload).encode("utf-8")
        request = Request(
            f"{GITHUB_API_BASE_URL}/{path}",
            data=body,
            method=method,
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {self._token}",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        try:
            with urlopen(request, timeout=30) as response:
                content = response.read()
        except HTTPError as error:
            if allow_not_found and error.code == 404:
                return None
            detail = error.read().decode("utf-8", errors="replace")
            raise SyncError(
                f"GitHub retornou HTTP {error.code} em {method} {path}: {detail}"
            ) from error
        except URLError as error:
            raise SyncError(
                f"Falha de conexão com o GitHub em {method} {path}: {error.reason}"
            ) from error

        if not content:
            return {}
        result = json.loads(content)
        if not isinstance(result, dict):
            raise SyncError(f"Resposta inválida do GitHub em {method} {path}.")
        return result

    def set_environment_variable(
        self, repository: str, environment: str, name: str, value: str
    ) -> None:
        base_path = (
            f"repos/{quote(repository, safe='/')}/environments/"
            f"{quote(environment, safe='')}/variables"
        )
        variable_path = f"{base_path}/{quote(name, safe='')}"
        existing = self._request("GET", variable_path, allow_not_found=True)
        if existing is None:
            self._request("POST", base_path, {"name": name, "value": value})
            return
        self._request("PATCH", variable_path, {"name": name, "value": value})


def _variable_attribute(variable: Mapping[str, object], name: str) -> object:
    attributes = variable.get("attributes")
    if not isinstance(attributes, dict):
        return None
    return attributes.get(name)


def normalized_backend_url(value: str) -> str:
    parsed = urlparse(value.strip())
    if (
        parsed.scheme not in ("http", "https")
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
        or parsed.path not in ("", "/")
    ):
        raise SyncError("URL pública do Backend inválida.")
    return value.strip().rstrip("/")


def sync_outputs(
    hcp_client: HcpClient,
    github_client: GitHubClient,
    backend_url: str,
    organization: str,
    auth_workspace: str,
    newrelic_workspace: str,
    backend_repository: str,
    kubernetes_repository: str,
    environment: str,
) -> None:
    normalized_url = normalized_backend_url(backend_url)
    health_check_url = f"{normalized_url}/actuator/health"

    hcp_client.set_workspace_variables(
        organization,
        auth_workspace,
        {"backend_base_url": (normalized_url, False)},
    )
    hcp_client.set_workspace_variables(
        organization,
        newrelic_workspace,
        {
            "health_check_url": (health_check_url, False),
            "synthetic_monitor_enabled": ("true", True),
        },
    )
    github_client.set_environment_variable(
        backend_repository, environment, "BACKEND_BASE_URL", normalized_url
    )
    github_client.set_environment_variable(
        kubernetes_repository, environment, "HEALTH_CHECK_URL", health_check_url
    )
    github_client.set_environment_variable(
        kubernetes_repository, environment, "SYNTHETIC_MONITOR_ENABLED", "true"
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Sincroniza a URL pública do Backend com Auth e New Relic."
    )
    parser.add_argument("--backend-url", required=True)
    parser.add_argument(
        "--organization", default=os.environ.get("TF_CLOUD_ORGANIZATION")
    )
    parser.add_argument("--auth-workspace", required=True)
    parser.add_argument("--newrelic-workspace", required=True)
    parser.add_argument(
        "--backend-repository",
        default="tiagomiele/fiap-tc-fase4-oficina-os-service",
    )
    parser.add_argument(
        "--kubernetes-repository",
        default="tiagomiele/fiap-tc-fase4-oficina-kubernetes-infra",
    )
    parser.add_argument("--environment", choices=("homolog", "production"), required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.organization:
        raise SyncError("TF_CLOUD_ORGANIZATION não configurada.")

    hcp_token = os.environ.get("TF_API_TOKEN") or os.environ.get("TFC_TOKEN") or ""
    github_token = os.environ.get("GITHUB_SYNC_TOKEN") or ""
    sync_outputs(
        HcpClient(hcp_token),
        GitHubClient(github_token),
        args.backend_url,
        args.organization,
        args.auth_workspace,
        args.newrelic_workspace,
        args.backend_repository,
        args.kubernetes_repository,
        args.environment,
    )
    print(
        "URL do Backend sincronizada com Auth, New Relic e GitHub Environments "
        f"({args.environment})."
    )
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, json.JSONDecodeError, SyncError) as error:
        print(f"::error::{error}", file=sys.stderr)
        sys.exit(1)
