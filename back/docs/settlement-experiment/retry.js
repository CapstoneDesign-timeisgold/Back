import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';
const success = new Rate('successful_settlement');
// Baseline conflicts are expected observations, not transport failures.
http.setResponseCallback(http.expectedStatuses(204, 409));
export const options = {
  scenarios: { retries: { executor: 'shared-iterations', vus: 10, iterations: 100, maxDuration: '60s' } },
  thresholds: { checks: ['rate==1'], ...(__ENV.AFTER === 'true' ? {successful_settlement: ['rate==1']} : {}) },
};
export default function () {
  const response = http.post(`${__ENV.BASE_URL}/settlement/reward`, JSON.stringify({promiseId: Number(__ENV.PROMISE_ID)}),
    {headers: {Authorization: `Bearer ${__ENV.TOKEN}`, 'Content-Type': 'application/json'}});
  success.add(response.status === 204);
  check(response, {'expected settlement response': r => r.status === 204 || r.status === 409});
}
export function handleSummary(data) { return {[__ENV.SUMMARY_FILE]: JSON.stringify(data, null, 2)}; }
