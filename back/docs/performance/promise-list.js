import http from 'k6/http';
import { check } from 'k6';

const expected = JSON.parse(open(__ENV.EXPECTED_FILE));
const expectedById = new Map(expected.map((row) => [row.promiseId, row]));
const fields = ['promiseId', 'title', 'date', 'time', 'creatorUsername',
  'participationDeadline', 'participationOpen'];

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '10s',
  summaryTrendStats: ['avg', 'med', 'min', 'max', 'p(95)', 'p(99)'],
  thresholds: {
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
  },
};

export default function () {
  const response = http.get(`${__ENV.BASE_URL}/promise`, {
    headers: { Authorization: `Bearer ${__ENV.TOKEN}` },
    timeout: '30s',
  });
  check(response, {
    'status 200': (r) => r.status === 200,
    'all DTO fields match expected data': (r) => {
      if (r.status !== 200) return false;
      try {
        const rows = r.json();
        return Array.isArray(rows) && rows.length === expected.length
          && new Set(rows.map((row) => row.promiseId)).size === expected.length
          && rows.every((row) => {
            const wanted = expectedById.get(row.promiseId);
            return wanted && fields.every((field) => row[field] === wanted[field]);
          });
      } catch (_) {
        return false;
      }
    },
  });
}

export function handleSummary(data) {
  return { [__ENV.SUMMARY_FILE]: JSON.stringify(data, null, 2) };
}
