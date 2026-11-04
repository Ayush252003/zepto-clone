import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

export const options = {
  vus: 5,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const response = http.get(`${baseUrl}/api/v1/categories`);
  check(response, {
    'catalog responds successfully': (r) => r.status === 200,
    'catalog returns JSON': (r) => (r.headers['Content-Type'] || '').includes('application/json'),
  });
  sleep(1);
}
