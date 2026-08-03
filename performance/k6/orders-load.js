import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const businessErrors = new Rate('business_errors');
const baseUrl = __ENV.ORDERS_BASE_URL || 'http://localhost:8081';

export const options = {
  scenarios: {
    steady_load: {
      executor: 'ramping-vus',
      stages: [
        { duration: '20s', target: 10 },
        { duration: '40s', target: 10 },
        { duration: '20s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    business_errors: ['rate<0.01'],
  },
};

export default function () {
  const payload = JSON.stringify({ userId: 1, item: `Book-${__VU}-${__ITER}`, amount: 12.5 });
  const response = http.post(`${baseUrl}/orders/new`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'X-Correlation-Id': `k6-${__VU}-${__ITER}`,
    },
  });

  const valid = check(response, {
    'created successfully': (r) => r.status === 200,
    'returns order id': (r) => {
      try { return /^order-\d+$/.test(r.json('order.id')); } catch (_) { return false; }
    },
  });

  businessErrors.add(!valid);
  sleep(0.2);
}
