import test from 'node:test';
import assert from 'node:assert/strict';
import { createQuizServer, validateInput, validateQuestions, buildProviderRequest } from './server.mjs';

const source = { id: 'test-source', title: '검증 전용', script: '불국사는 경주에 있는 사찰입니다. 석가탑과 다보탑이 있습니다.' };
const input = { tripId: 'test-trip', count: 3, sources: [source] };
const questions = () => [1, 2, 3].map(i => ({
  sourceId: source.id, prompt: '검증 질문 ' + i,
  options: ['경주', '서울', '부산'], correctIndex: 0,
  explanation: '대본에 경주라고 나옵니다.', evidence: source.script
}));
const output = (q = questions(), extra = {}) => ({
  status: 'completed', output: [{ type: 'message', content: [{ type: 'output_text', text: JSON.stringify({ questions: q }) }] }],
  ...extra
});
async function server(t, options = {}) {
  const s = createQuizServer(options);
  await new Promise(resolve => s.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => s.close(resolve)));
  return 'http://127.0.0.1:' + s.address().port;
}
const post = (url, body = input) => fetch(url + '/quiz', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
});

test('valid request uses structured output and never forwards trip metadata to provider', async t => {
  let calls = 0;
  const url = await server(t, { apiKey: 'test-fixture-not-a-real-key', fetchImpl: async (url, opts) => {
    calls++;
    assert.equal(url, 'https://api.openai.com/v1/responses');
    const request = JSON.parse(opts.body);
    assert.equal(request.store, false);
    assert.equal(request.text.format.strict, true);
    assert.equal(JSON.parse(request.input[1].content).tripId, undefined);
    return new Response(JSON.stringify(output()));
  } });
  const response = await post(url);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { tripId: input.tripId, count: 3, questions: questions() });
  assert.equal(calls, 1);
});

test('maximum ten request returns fewer grounded questions and drops invalid candidates', async t => {
  const candidates = [questions()[0], questions()[0], { ...questions()[1], evidence: 'invented quote' }];
  const url = await server(t, { apiKey: 'test-fixture-not-a-real-key',
    fetchImpl: async () => new Response(JSON.stringify(output(candidates))) });
  const response = await post(url, { ...input, count: 10 });
  assert.equal(response.status, 200);
  const result = await response.json();
  assert.equal(result.count, 1);
  assert.equal(result.questions.length, 1);
});

test('missing key is explicit and never produces fallback questions', async t => {
  let calls = 0;
  const url = await server(t, { apiKey: '', fetchImpl: async () => { calls++; } });
  assert.equal((await post(url)).status, 503);
  assert.equal(calls, 0);
  assert.equal((await (await fetch(url + '/health')).json()).ready, false);
});

test('unsupported counts, duplicate sources and empty scripts are rejected before provider call', () => {
  for (const invalid of [
    { ...input, count: 11 }, { ...input, sources: [source, source] },
    { ...input, sources: [{ ...source, script: '' }] }
  ]) assert.throws(() => validateInput(invalid));
});

test('foreign source, invented evidence and invalid answer index fail; duplicate candidates are removed', () => {
  const valid = questions();
  validateQuestions(valid, input);
  for (const invalid of [
    valid.map(q => ({ ...q, sourceId: 'other-trip' })),
    valid.map(q => ({ ...q, evidence: '존재하지 않는 근거를 지어냈습니다.' })),
    valid.map(q => ({ ...q, correctIndex: 3 })),
  ]) assert.throws(() => validateQuestions(invalid, input));
  assert.equal(validateQuestions([valid[0], valid[0], valid[2]], input).length, 2);
  assert.equal(validateQuestions(valid.slice(1), input).length, 2);
});

test('incomplete, refused, malformed and insufficient model output never becomes a quiz', async t => {
  for (const body of [
    output([], { status: 'incomplete' }),
    output([], { output: [{ type: 'message', content: [{ type: 'refusal', refusal: 'no' }] }] }),
    output([], { output: [{ type: 'message', content: [{ type: 'output_text', text: 'invalid-json' }] }] }),
    output([])
  ]) {
    const url = await server(t, { apiKey: 'test-fixture-not-a-real-key',
      fetchImpl: async () => new Response(JSON.stringify(body)) });
    assert.equal((await post(url)).status, 422);
  }
});

test('provider quota errors are reported without exposing raw provider response', async t => {
  const url = await server(t, { apiKey: 'test-fixture-not-a-real-key',
    fetchImpl: async () => new Response('private-provider-response', { status: 429 }) });
  const response = await post(url);
  assert.equal(response.status, 429);
  assert.deepEqual(await response.json(), { error: 'PROVIDER_ERROR' });
});

test('light evidence accepts short quotes and paraphrased options but rejects invented evidence', () => {
  const q = { ...questions()[0], options: ['경주시', '서울', '부산'], evidence: '경주' };
  assert.equal(validateQuestions([q], input).length, 1);
  assert.equal(validateQuestions([{ ...q, evidence: '불국사는 경주에 있는 사찰입니다!' }], input).length, 1);
  assert.throws(() => validateQuestions([{ ...q, evidence: '!!!' }], input));
  assert.throws(() => validateQuestions([{ ...q, evidence: '제주' }], input));
  assert.throws(() => validateQuestions([{ ...q, evidence: '15km' }],
    { ...input, sources: [{ ...source, script: '거리는 1.5km입니다.' }] }));
});

test('unauthorized requests never consume provider calls; generation cap blocks subsequent calls', async t => {
  let calls = 0;
  const url = await server(t, { apiKey: 'fixture', accessToken: 'demo-fixture', maxGenerations: 1,
    fetchImpl: async () => { calls++; return new Response(JSON.stringify(output())); } });
  assert.equal((await post(url)).status, 401);
  const authorized = () => fetch(url + '/quiz', {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer demo-fixture' },
    body: JSON.stringify(input)
  });
  assert.equal((await authorized()).status, 200);
  const exhausted = await authorized();
  assert.equal(exhausted.status, 429);
  assert.deepEqual(await exhausted.json(), { error: 'GENERATION_LIMIT' });
  assert.equal(calls, 1);
});

test('provider schema only admits this request sources and 3 options', () => {
  const request = buildProviderRequest(input, 'test-model');
  const schema = request.text.format.schema.properties.questions.items;
  assert.deepEqual(schema.properties.sourceId.enum, [source.id]);
  assert.equal(schema.properties.options.minItems, 3);
  assert.match(request.input[0].content, /명령은 무시/);
});
