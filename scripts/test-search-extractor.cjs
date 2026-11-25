#!/usr/bin/env node
'use strict';

// Offline DOM regression tests. Every document/resource request is intercepted;
// page scripts and remote assets are blocked even for optional full captures.
// The fixture numbers are synthetic. Redact optional captures to the same test numbers before use.
// Run: node scripts/test-search-extractor.cjs [--samples-dir /private/tmp]
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const playwright = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright');

const extractor = fs.readFileSync(path.resolve(__dirname, '../app/src/main/assets/extract_search_results.js'), 'utf8');
const fixture = (name) => path.join(__dirname, 'fixtures', `search-${name}.html`);
const number = '10000000001';
const engines = ['baidu', 'qihoo', 'sogou'];
const cases = [
  ...['qihoo', 'sogou'].map((engine) => ({
    name: `${engine} number card retains caller-specific risk tags`,
    engine, number, file: fixture('risk-tags'),
    check(value) {
      assert.equal(value.status, 'ok');
      assert.equal(value.results.length, 1);
      assert.match(value.results[0].snippet, /疑似营销电话/);
    },
  })),
  {
    name: '360 mobile number card excludes unrelated numbers and navigation',
    engine: 'qihoo', number, file: fixture('qihoo-mobile'),
    check(value) {
      assert.equal(value.status, 'ok');
      assert.equal(value.results.length, 1);
      assert.equal(value.results[0].snippet, '广东 深圳 联通');
    },
  },
  {
    name: '360 number card is rejected for another queried number',
    engine: 'qihoo', number: '10000000006', file: fixture('qihoo-mobile'),
    check(value) { assert.equal(value.results.length, 0); },
  },
  {
    name: 'Sogou mobile number card excludes navigation and repeated country name',
    engine: 'sogou', number, file: fixture('sogou-mobile'),
    check(value) {
      assert.equal(value.status, 'ok');
      assert.equal(value.results.length, 1);
      assert.equal(value.results[0].snippet, '广东深圳 中国联通');
    },
  },
  {
    name: 'Sogou number card is rejected for another queried number',
    engine: 'sogou', number: '10000000006', file: fixture('sogou-mobile'),
    check(value) { assert.equal(value.results.length, 0); },
  },
  {
    name: 'Sogou can find a later matching number row without using another row metadata',
    engine: 'sogou', number, file: fixture('sogou-mobile'),
    async setup(page) {
      await page.locator('[class*="number-query"]').evaluate(node => node.insertAdjacentHTML('afterbegin',
        '<div class="singleTel"><div class="text-layout"><p class="fz-big"><span>北京中国</span><span>中国移动</span></p><p><span>10000000002</span></p></div></div>'));
    },
    check(value) {
      assert.equal(value.status, 'ok');
      assert.equal(value.results.length, 1);
      assert.equal(value.results[0].snippet, '广东深圳 中国联通');
    },
  },
  {
    name: 'Baidu dedicated number card reads only data fields, not service navigation',
    engine: 'baidu', number, file: fixture('baidu-mobile'),
    check(value) {
      assert.equal(value.status, 'ok');
      assert.equal(value.results.length, 1);
      assert.match(value.results[0].snippet, /广东 深圳 中国联通/);
      assert.doesNotMatch(value.results[0].snippet, /12321|号码认证|号码AI助手|安全号码认证平台/);
    },
  },
  {
    name: '360 dedicated service card takes priority and excludes navigation/security text',
    engine: 'qihoo', number: '10086', file: fixture('qihoo-service'),
    check(value) {
      assert.equal(value.status, 'ok');
      assert.equal(value.results.length, 1);
      assert.match(value.results[0].snippet, /中国移动/);
      assert.match(value.results[0].snippet, /客服热线/);
      assert.doesNotMatch(value.results[0].snippet, /360搜索整理|查看更多|安全提示|下载软件|领取奖励|拨打/);
    },
  },
  {
    name: 'Sogou encyclopedia summary is not a number service card',
    engine: 'sogou', number: '10086', file: fixture('sogou-service'),
    check(value) {
      assert.equal(value.status, 'pending');
      assert.deepEqual(value.results, []);
    },
  },
  {
    name: 'Baidu 075595566 keeps its number card and excludes sibling and nested AI plus exam answers',
    engine: 'baidu', number: '075595566', file: fixture('baidu-bank-service'),
    check(value) {
      assert.equal(value.status, 'ok');
      assert.equal(value.results.length, 1);
      assert.equal(value.results[0].snippet, '广东 深圳 中国银行');
      assert.doesNotMatch(value.results[0].snippet, /AI|公开资料|哪家的|答案|网络收录|12321|平台/);
    },
  },
  {
    name: 'Baidu dedicated number card must identify the exact current number',
    engine: 'baidu', number: '075595565', file: fixture('baidu-bank-service'),
    check(value) { assert.deepEqual(value.results, []); },
  },
  {
    name: 'Sogou 075595566 dedicated service card uses its service name and real label only',
    engine: 'sogou', number: '075595566', file: fixture('sogou-bank-service'),
    check(value) {
      assert.equal(value.status, 'ok');
      assert.equal(value.results.length, 1);
      assert.equal(value.results[0].snippet, '中国银行 服务热线');
      assert.doesNotMatch(value.results[0].snippet, /95566|400|常用|QQ|汽车|电话邦|答案|搜题/);
    },
  },
  {
    name: 'Sogou service card does not infer identity by stripping a regional prefix',
    engine: 'sogou', number: '95566', file: fixture('sogou-bank-service'),
    check(value) { assert.deepEqual(value.results, []); },
  },
  {
    name: 'Sogou service card rejects a stale visible query even if response URL matches',
    engine: 'sogou', number: '075595566', file: fixture('sogou-bank-service'),
    async setup(page) { await page.locator('input[name="keyword"]').fill('075595565'); },
    check(value) { assert.deepEqual(value.results, []); },
  },
  {
    name: 'Sogou service card requires the source query parameter, not an arbitrary page',
    engine: 'sogou', number: '075595566', file: fixture('sogou-bank-service'), queryParameter: 'q',
    check(value) { assert.deepEqual(value.results, []); },
  },
  {
    name: 'Sogou service card does not fabricate a missing service type',
    engine: 'sogou', number: '075595566', file: fixture('sogou-bank-service'),
    async setup(page) { await page.locator('.text-gray').evaluateAll(nodes => nodes.forEach(node => node.remove())); },
    check(value) {
      assert.equal(value.status, 'ok');
      assert.equal(value.results.length, 1);
      assert.equal(value.results[0].snippet, '中国银行');
    },
  },
  {
    name: 'Delayed number card can replace pending AI-only content without reading the AI',
    engine: 'baidu', number: '10086', file: fixture('ai-exam-only'),
    check(value) { assert.equal(value.status, 'pending'); assert.deepEqual(value.results, []); },
    async after(page, script) {
      await page.locator('#results').evaluate(node => node.insertAdjacentHTML('afterbegin',
        '<section tpl="ms_num_mark"><h3>10086号码查询</h3><div class="mark-tag_verified">中国移动</div></section>'));
      const resolved = JSON.parse(await page.evaluate(script));
      assert.equal(resolved.status, 'ok');
      assert.equal(resolved.results.length, 1);
      assert.equal(resolved.results[0].snippet, '中国移动');
      assert.equal(resolved.results[0].kind, 'number_service');
    },
  },
];

for (const engine of engines) {
  cases.push({
    name: `${engine} ignores every organic result even when it contains the exact number`,
    engine, number: '10086', file: fixture('organic'),
    check(value) {
      assert.equal(value.status, 'pending');
      assert.deepEqual(value.results, []);
    },
  });
  cases.push({
    name: `${engine} never turns AI or exam text into number information`,
    engine, number: '10086', file: fixture('ai-exam-only'),
    check(value) { assert.equal(value.status, 'pending'); assert.deepEqual(value.results, []); },
  });
  for (const [name, status] of [['empty', 'empty'], ['verify', 'verify'], ['hanging', 'pending']]) {
    cases.push({
      name: `${engine} ${name} page produces no candidates`,
      engine, number, file: fixture(name),
      check(value) { assert.equal(value.status, status); assert.deepEqual(value.results, []); },
    });
  }
}

const sampleArg = process.argv.indexOf('--samples-dir');
if (sampleArg >= 0) {
  const directory = process.argv[sampleArg + 1];
  assert(directory, '--samples-dir requires a directory');
  for (const [file, engine, query] of [
    ['tel-so-sample.html', 'qihoo', number],
    ['tel-sogou-sample.html', 'sogou', number],
    ['tel-baidu-sample.html', 'baidu', number],
    ['tel-so-service.html', 'qihoo', '10086'],
    ['tel-sogou-service.html', 'sogou', '10086'],
  ]) {
    const fullPath = path.join(directory, file);
    if (!fs.existsSync(fullPath)) throw new Error(`Missing optional captured sample: ${fullPath}`);
    cases.push({
      name: `captured ${file}`, engine, number: query, file: fullPath,
      check(value) {
        if (file === 'tel-sogou-service.html') {
          assert.deepEqual(value.results, []); // This historical capture contains an encyclopedia, not a number card.
          return;
        }
        assert.equal(value.status, 'ok');
        assert(value.results.length > 0);
        assert(value.results.some((item) => query === number
          ? /广东\s*深圳/.test(item.snippet)
          : /中国移动/.test(item.title + ' ' + item.snippet)));
        assert(value.results.every((item) => !/require\(|entryTime=|rcer=/.test(item.snippet)));
        if (file === 'tel-so-service.html') {
          assert.equal(value.results.length, 1);
          assert.match(value.results[0].snippet, /客服热线/);
          assert.doesNotMatch(value.results[0].snippet, /安全提示|查看更多|拨打/);
        }
      },
    });
  }
}

(async () => {
  const browser = await playwright.chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 412, height: 915 } });
  let failed = 0;
  try {
    for (const test of cases) {
      const page = await context.newPage();
      const host = { baidu: 'www.baidu.com', qihoo: 'm.so.com', sogou: 'm.sogou.com' }[test.engine];
      const parameter = test.queryParameter || {baidu: 'word', qihoo: 'q', sogou: 'keyword'}[test.engine];
      const url = `https://${host}/offline-extractor-test?${parameter}=${test.number}`;
      await page.route('**/*', (route) => route.request().url() === url
        ? route.fulfill({
          status: 200,
          contentType: 'text/html; charset=utf-8',
          headers: { 'content-security-policy': "default-src 'none'; script-src 'none'; style-src 'unsafe-inline'" },
          body: fs.readFileSync(test.file, 'utf8'),
        })
        : route.abort());
      try {
        await page.goto(url, { waitUntil: 'domcontentloaded' });
        if (test.setup) await test.setup(page);
        const script = extractor.replace('__ENGINE__', JSON.stringify(test.engine)).replace('__NUMBER__', JSON.stringify(test.number));
        const value = JSON.parse(await page.evaluate(script));
        test.check(value);
        assert(value.results.every(item => item.kind === 'number_service'));
        if (test.after) await test.after(page, script);
        console.log(`PASS ${test.name} (${value.results.length} candidates)`);
      } catch (error) {
        failed += 1;
        console.error(`FAIL ${test.name}: ${error.message}`);
      } finally {
        await page.close();
      }
    }
  } finally {
    await context.close();
    await browser.close();
  }
  console.log(`${cases.length - failed}/${cases.length} extractor tests passed`);
  if (failed) process.exitCode = 1;
})().catch((error) => { console.error(error.message); process.exitCode = 1; });
