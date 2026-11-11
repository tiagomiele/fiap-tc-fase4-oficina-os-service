import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const backendBaseUrl = (__ENV.BACKEND_BASE_URL || '').replace(/\/$/, '');
const apiGatewayBaseUrl = (__ENV.API_GATEWAY_BASE_URL || '').replace(/\/$/, '');
const ownerToken = __ENV.OWNER_TOKEN || '';
const numeroOs = __ENV.NUMERO_OS || '';

if (!backendBaseUrl) {
  throw new Error('BACKEND_BASE_URL e obrigatoria');
}

const protectedLatency = new Trend('protected_route_duration', true);
const protectedFailures = new Rate('protected_route_failures');
const healthFailures = new Counter('health_failures');

export const options = {
  scenarios: {
    health: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.RAMP_UP || '15s', target: Number(__ENV.VUS || 5) },
        { duration: __ENV.HOLD || '30s', target: Number(__ENV.VUS || 5) },
        { duration: '15s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    protected_route_failures: ['rate<0.01'],
    protected_route_duration: ['p(95)<1500'],
  },
};

export default function () {
  const requestId = `k6-${__VU}-${__ITER}-${Date.now()}`;
  const health = http.get(`${backendBaseUrl}/actuator/health/readiness`, {
    headers: { 'X-Request-Id': requestId },
    tags: { operation: 'health-readiness' },
  });
  const healthOk = check(health, {
    'health retorna 200': (response) => response.status === 200,
    'health devolve request id': (response) => Boolean(response.headers['X-Request-Id']),
  });
  if (!healthOk) healthFailures.add(1);

  if (apiGatewayBaseUrl && ownerToken && numeroOs) {
    const status = http.get(
      `${apiGatewayBaseUrl}/consulta/ordens-servico/${encodeURIComponent(numeroOs)}/status`,
      {
        headers: {
          Authorization: `Bearer ${ownerToken}`,
          'X-Request-Id': requestId,
        },
        tags: { operation: 'customer-os-status' },
      },
    );
    protectedLatency.add(status.timings.duration);
    protectedFailures.add(status.status !== 200);
    check(status, { 'proprietario consulta OS': (response) => response.status === 200 });
  }

  sleep(Number(__ENV.SLEEP_SECONDS || 1));
}
