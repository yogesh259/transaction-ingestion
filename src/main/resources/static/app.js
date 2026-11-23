'use strict';

/* ════════════════════════════ API ════════════════════════════ */
const API = '/api/v1';

async function api(path, opts = {}) {
  const res = await fetch(API + path, {
    headers: opts.body ? { 'Content-Type': 'application/json' } : {},
    ...opts,
    body: opts.body ? JSON.stringify(opts.body) : undefined,
  });
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText}`;
    try { const e = await res.json(); if (e.message) msg = e.message; } catch { /* not JSON */ }
    throw new Error(msg);
  }
  return res.status === 204 || res.status === 202 && !res.headers.get('content-type') ? null : res.json();
}

/* ════════════════════════════ Helpers ════════════════════════════ */
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const state = { currency: 'INR', categories: [], txPage: 0 };

function money(v, compact = false) {
  const n = Number(v ?? 0);
  if (compact) return compactMoney(n);
  const locale = state.currency === 'INR' ? 'en-IN' : 'en-US';
  try {
    return new Intl.NumberFormat(locale, {
      style: 'currency', currency: state.currency,
      maximumFractionDigits: compact || Math.abs(n) >= 1000 ? 0 : 2,
      notation: compact ? 'compact' : 'standard',
    }).format(n);
  } catch { return n.toFixed(2); }
}

/** Axis labels: ₹12K / ₹1.5L / ₹2Cr for INR, $12K / $1.5M otherwise. */
function compactMoney(n) {
  const sym = (0).toLocaleString('en', { style: 'currency', currency: state.currency, maximumFractionDigits: 0 }).replace(/[\d\s.,]/g, '');
  const a = Math.abs(n);
  const fmt = (x, u) => `${sym}${Number.isInteger(x) ? x : x.toFixed(1)}${u}`;
  if (state.currency === 'INR') {
    if (a >= 1e7) return fmt(n / 1e7, 'Cr');
    if (a >= 1e5) return fmt(n / 1e5, 'L');
  } else {
    if (a >= 1e9) return fmt(n / 1e9, 'B');
    if (a >= 1e6) return fmt(n / 1e6, 'M');
  }
  if (a >= 1e3) return fmt(n / 1e3, 'K');
  return fmt(n, '');
}

function toast(msg, ms = 3500) {
  const t = $('#toast');
  t.textContent = msg; t.hidden = false;
  clearTimeout(toast._t); toast._t = setTimeout(() => { t.hidden = true; }, ms);
}

function cssVar(name) { return getComputedStyle(document.documentElement).getPropertyValue(name).trim(); }

/* ════════════════════════════ Routing ════════════════════════════ */
const views = ['dashboard', 'transactions', 'import', 'rules'];

function route() {
  const [name, query] = (location.hash.slice(1) || 'dashboard').split('?');
  const view = views.includes(name) ? name : 'dashboard';
  views.forEach((v) => { $('#view-' + v).hidden = v !== view; });
  $$('.tabs a').forEach((a) => a.classList.toggle('active', a.dataset.tab === view));
  if (view === 'dashboard') loadDashboard();
  if (view === 'transactions') {
    const params = new URLSearchParams(query || '');
    if (params.has('category')) $('#tx-filters [name=category]').dataset.pending = params.get('category');
    loadTransactionsView();
  }
  if (view === 'rules') loadRules();
}
window.addEventListener('hashchange', route);

/* ════════════════════════════ Dashboard ════════════════════════════ */
const charts = {};

async function detectCurrency() {
  const page = await api('/transactions?size=1&sort=transactionDate,desc');
  if (page.content.length) state.currency = page.content[0].currency;
  return page;
}

async function loadDashboard() {
  const [latest, summary, status] = await Promise.all([
    detectCurrency(), api('/analytics/summary'), api('/categorise/status'),
  ]).catch((e) => { toast('Could not load dashboard: ' + e.message); return []; });
  if (!summary) return;

  const empty = latest.totalElements === 0;
  $('#dash-empty').hidden = !empty;
  $('#dash-content').hidden = empty;
  if (empty) return;

  state.categories = summary.totalSpendByCategory.map((c) => c.category);
  const cats = summary.totalSpendByCategory;
  const months = summary.monthOverMonthDelta;
  const total = cats.reduce((s, c) => s + Number(c.totalSpend), 0);

  // KPIs
  $('#kpi-total').textContent = money(total);
  $('#kpi-total-sub').textContent = `${latest.totalElements.toLocaleString()} transactions`;
  if (months.length) {
    const last = months[months.length - 1];
    $('#dash-range').textContent = `${months[0].month} → ${last.month} · ${months.length} month${months.length > 1 ? 's' : ''}`;
    $('#kpi-month').textContent = money(last.totalSpend);
    if (last.deltaPct != null) {
      const up = Number(last.deltaPct) > 0;
      $('#kpi-month-sub').innerHTML = `<span class="${up ? 'delta-up' : 'delta-down'}">${up ? '▲' : '▼'} ${Math.abs(last.deltaPct)}%</span> vs ${esc(months[months.length - 2]?.month ?? 'previous')} · ${esc(last.month)}`;
    } else {
      $('#kpi-month-sub').textContent = last.month;
    }
  }
  const top = cats.find((c) => c.category !== 'Uncategorised') ?? cats[0];
  $('#kpi-topcat').textContent = top ? top.category : '–';
  $('#kpi-topcat-sub').textContent = top ? `${money(top.totalSpend)} · ${Math.round((top.totalSpend / total) * 100)}% of spend` : '';
  const done = status.totalMerchants - status.uncategorisedMerchants;
  $('#kpi-cov').textContent = `${done} / ${status.totalMerchants}`;
  $('#kpi-cov-sub').textContent = status.uncategorisedMerchants
    ? `${status.uncategorisedMerchants} still uncategorised`
    : 'All merchants categorised';

  renderCharts(cats, summary.topMerchants, months, total);
  if (status.running) watchCategorisation();
}

function baseChartOptions() {
  const grid = cssVar('--border');
  const ink = cssVar('--text-2');
  return {
    responsive: true, maintainAspectRatio: false, animation: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: cssVar('--text'), titleColor: cssVar('--bg'), bodyColor: cssVar('--bg'),
        padding: 10, cornerRadius: 8, displayColors: false,
      },
    },
    scales: {
      x: { grid: { color: grid, drawTicks: false }, border: { display: false }, ticks: { color: ink, padding: 6 } },
      y: { grid: { color: grid, drawTicks: false }, border: { display: false }, ticks: { color: ink, padding: 6 } },
    },
  };
}

function renderCharts(cats, merchants, months, total) {
  if (!window.Chart) { setTimeout(() => renderCharts(cats, merchants, months, total), 100); return; }
  Chart.defaults.font.family = 'Inter, system-ui, sans-serif';
  Chart.defaults.font.size = 12;
  const accent = cssVar('--accent');
  const muted = cssVar('--border');

  // Category — horizontal bars, single hue; "Uncategorised" greyed out.
  const catOpts = baseChartOptions();
  catOpts.indexAxis = 'y';
  catOpts.scales.x.ticks.callback = (v) => money(v, true);
  catOpts.scales.y.grid.display = false;
  catOpts.plugins.tooltip.callbacks = {
    label: (ctx) => `${money(ctx.raw)} · ${((ctx.raw / total) * 100).toFixed(1)}% of spend`,
  };
  catOpts.onClick = (_e, els) => {
    if (els.length) location.hash = 'transactions?category=' + encodeURIComponent(cats[els[0].index].category);
  };
  catOpts.onHover = (e, els) => { e.native.target.style.cursor = els.length ? 'pointer' : 'default'; };
  $('#box-category').style.height = Math.max(220, cats.length * 34 + 40) + 'px';
  drawChart('category', 'bar', {
    labels: cats.map((c) => c.category),
    datasets: [{
      data: cats.map((c) => Number(c.totalSpend)),
      backgroundColor: cats.map((c) => (c.category === 'Uncategorised' ? muted : accent)),
      borderRadius: 4, borderSkipped: 'start', barPercentage: 0.8, categoryPercentage: 0.9,
    }],
  }, catOpts);

  // Top merchants
  const mOpts = baseChartOptions();
  mOpts.indexAxis = 'y';
  mOpts.scales.x.ticks.callback = (v) => money(v, true);
  mOpts.scales.y.grid.display = false;
  mOpts.scales.y.ticks.callback = function (v) { const l = this.getLabelForValue(v); return l.length > 22 ? l.slice(0, 21) + '…' : l; };
  mOpts.plugins.tooltip.callbacks = { label: (ctx) => money(ctx.raw) };
  $('#box-merchants').style.height = Math.max(220, merchants.length * 34 + 40) + 'px';
  drawChart('merchants', 'bar', {
    labels: merchants.map((m) => m.merchantName),
    datasets: [{
      data: merchants.map((m) => Number(m.totalSpend)), backgroundColor: accent,
      borderRadius: 4, borderSkipped: 'start', barPercentage: 0.8, categoryPercentage: 0.9,
    }],
  }, mOpts);

  // Monthly
  const moOpts = baseChartOptions();
  moOpts.scales.y.ticks.callback = (v) => money(v, true);
  moOpts.scales.x.grid.display = false;
  moOpts.layout = { padding: { top: 22 } };
  moOpts.plugins.tooltip.callbacks = {
    label: (ctx) => money(ctx.raw),
    afterLabel: (ctx) => {
      const d = months[ctx.dataIndex].deltaPct;
      return d == null ? 'No previous month' : `${d > 0 ? '+' : ''}${d}% vs previous month`;
    },
  };
  drawChart('monthly', 'bar', {
    labels: months.map((m) => formatMonth(m.month)),
    datasets: [{
      data: months.map((m) => Number(m.totalSpend)), backgroundColor: accent,
      borderRadius: 4, borderSkipped: 'start', maxBarThickness: 56,
    }],
  }, moOpts, [deltaLabels(months)]);
}

function deltaLabels(months) {
  return {
    id: 'deltaLabels',
    afterDatasetsDraw(chart) {
      const { ctx } = chart;
      ctx.save();
      ctx.font = '600 11px Inter, system-ui, sans-serif';
      ctx.textAlign = 'center';
      chart.getDatasetMeta(0).data.forEach((bar, i) => {
        const d = months[i].deltaPct;
        if (d == null) return;
        ctx.fillStyle = cssVar(Number(d) > 0 ? '--bad' : '--good');
        ctx.fillText(`${Number(d) > 0 ? '▲' : '▼'} ${Math.abs(d)}%`, bar.x, bar.y - 7);
      });
      ctx.restore();
    },
  };
}

function formatMonth(ym) {
  const [y, m] = ym.split('-').map(Number);
  return new Date(y, m - 1, 1).toLocaleString('en', { month: 'short', year: 'numeric' });
}

function drawChart(key, type, data, options, plugins = []) {
  charts[key]?.destroy();
  charts[key] = new Chart($('#chart-' + key), { type, data, options, plugins });
}

/* ════════════════════════════ Transactions ════════════════════════════ */
async function loadTransactionsView() {
  const sel = $('#tx-filters [name=category]');
  try {
    if (!state.categories.length) {
      await detectCurrency();
      state.categories = (await api('/analytics/summary')).totalSpendByCategory.map((c) => c.category);
    }
  } catch { /* table load reports errors */ }
  const current = sel.dataset.pending ?? sel.value;
  delete sel.dataset.pending;
  sel.innerHTML = '<option value="">All</option>' + state.categories
    .filter((c) => c !== 'Uncategorised')
    .map((c) => `<option ${c === current ? 'selected' : ''}>${esc(c)}</option>`).join('');
  state.txPage = 0;
  loadTransactions();
}

async function loadTransactions() {
  const f = new FormData($('#tx-filters'));
  const q = new URLSearchParams({ page: state.txPage, size: 25, sort: 'transactionDate,desc' });
  for (const [k, v] of f) if (v) q.set(k, v);
  const body = $('#tx-body');
  try {
    const page = await api('/transactions?' + q);
    body.innerHTML = page.content.length ? page.content.map(txRow).join('')
      : '<tr class="empty-row"><td colspan="6">No transactions match these filters.</td></tr>';
    const from = page.totalElements ? page.page * page.size + 1 : 0;
    $('#tx-count').textContent = `${from}–${page.page * page.size + page.content.length} of ${page.totalElements.toLocaleString()}`;
    $('#tx-prev').disabled = page.page === 0;
    $('#tx-next').disabled = page.last;
    body._rows = page.content;
  } catch (e) {
    body.innerHTML = `<tr class="empty-row"><td colspan="6">Could not load transactions: ${esc(e.message)}</td></tr>`;
  }
}

function txRow(t, i) {
  const date = new Date(t.transactionDate + 'T00:00:00').toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  const cat = t.category
    ? `<span class="pill">${esc(t.category)}</span>${t.subCategory ? `<span class="sub">${esc(t.subCategory)}</span>` : ''}`
    : '<span class="pill none">Uncategorised</span>';
  let method = '';
  if (t.strategyUsed === 'RULE_BASED') method = '<span class="badge rule" title="Matched a keyword rule">Rule</span>';
  if (t.strategyUsed === 'FALLBACK_LLM') method = `<span class="badge ai" title="Categorised by the LLM fallback">AI · ${Math.round(Number(t.confidenceScore) * 100)}%</span>`;
  return `<tr>
    <td style="white-space:nowrap">${date}</td>
    <td><div class="merchant">${esc(t.merchantName)}</div>${t.description ? `<div class="raw" title="${esc(t.description)}">${esc(t.description)}</div>` : ''}</td>
    <td>${cat}</td>
    <td>${method}</td>
    <td class="num">${money(t.amount)}</td>
    <td class="num"><button class="btn sm" data-fix="${i}" title="Create a rule for this merchant">Fix</button></td>
  </tr>`;
}

$('#tx-filters').addEventListener('change', () => { state.txPage = 0; loadTransactions(); });
$('#tx-filters').addEventListener('reset', () => setTimeout(() => { state.txPage = 0; loadTransactions(); }));
$('#tx-prev').addEventListener('click', () => { state.txPage--; loadTransactions(); });
$('#tx-next').addEventListener('click', () => { state.txPage++; loadTransactions(); });
$('#tx-body').addEventListener('click', (e) => {
  const btn = e.target.closest('[data-fix]');
  if (!btn) return;
  const t = $('#tx-body')._rows[Number(btn.dataset.fix)];
  openRuleDialog({
    keyword: t.merchantName, category: t.category ?? '', subCategory: t.subCategory ?? '', priority: 3, isActive: true,
  }, `Every merchant containing this keyword will get this category, overriding ${t.strategyUsed === 'FALLBACK_LLM' ? 'the AI result' : 'lower-precedence rules'}.`);
});

/* ════════════════════════════ Rules ════════════════════════════ */
async function loadRules() {
  const body = $('#rules-body');
  try {
    const rules = await api('/rules');
    body._rules = rules;
    body.innerHTML = rules.map((r, i) => `<tr>
      <td class="merchant">${esc(r.keyword)}</td>
      <td>${esc(r.category)}</td>
      <td class="muted">${esc(r.subCategory ?? '')}</td>
      <td class="num">${r.priority}</td>
      <td><input type="checkbox" class="toggle" data-toggle="${i}" ${r.isActive ? 'checked' : ''} aria-label="Active"></td>
      <td class="num"><button class="btn sm" data-edit="${i}">Edit</button></td>
    </tr>`).join('') || '<tr class="empty-row"><td colspan="6">No rules yet.</td></tr>';
  } catch (e) {
    body.innerHTML = `<tr class="empty-row"><td colspan="6">Could not load rules: ${esc(e.message)}</td></tr>`;
  }
}

$('#rules-body').addEventListener('click', async (e) => {
  const rules = $('#rules-body')._rules;
  const edit = e.target.closest('[data-edit]');
  if (edit) openRuleDialog(rules[Number(edit.dataset.edit)]);
  const tog = e.target.closest('[data-toggle]');
  if (tog) {
    const r = rules[Number(tog.dataset.toggle)];
    try {
      await api('/rules', { method: 'POST', body: { ...r, isActive: tog.checked } });
      r.isActive = tog.checked;
      toast(`Rule "${r.keyword}" ${tog.checked ? 'enabled' : 'disabled'}`);
    } catch (err) { tog.checked = !tog.checked; toast(err.message); }
  }
});
$('#rule-new').addEventListener('click', () => openRuleDialog({ keyword: '', category: '', subCategory: '', priority: 10, isActive: true }));

function openRuleDialog(rule, hint = '') {
  const form = $('#rule-form');
  form.keyword.value = rule.keyword; form.category.value = rule.category;
  form.subCategory.value = rule.subCategory ?? ''; form.priority.value = rule.priority;
  form.isActive.checked = rule.isActive;
  $('#rule-dialog-title').textContent = rule.keyword ? 'Edit rule' : 'New rule';
  $('#rule-dialog-hint').textContent = hint || 'Lower priority numbers win when several keywords match.';
  $('#rule-error').hidden = true;
  const known = new Set(['Food & Drink', 'Groceries', 'Shopping', 'Transport', 'Travel', 'Bills & Utilities',
    'Entertainment', 'Health', 'Investments', 'Insurance', 'Cash', 'Transfers', 'Rent', 'Education', ...state.categories]);
  known.delete('Uncategorised');
  $('#category-list').innerHTML = [...known].map((c) => `<option value="${esc(c)}">`).join('');
  $('#rule-dialog').showModal();
}

$('#rule-form').addEventListener('submit', async (e) => {
  if (e.submitter?.value !== 'save') return;
  e.preventDefault();
  const f = e.target;
  const body = {
    keyword: f.keyword.value.trim(), category: f.category.value.trim(),
    subCategory: f.subCategory.value.trim() || null, priority: Number(f.priority.value), isActive: f.isActive.checked,
  };
  $('#rule-save').disabled = true;
  try {
    await api('/rules', { method: 'POST', body });
    $('#rule-dialog').close();
    toast(`Rule saved — applying to existing merchants`);
    await triggerCategorisation(true);
    if (!$('#view-rules').hidden) loadRules();
  } catch (err) {
    $('#rule-error').textContent = err.message; $('#rule-error').hidden = false;
  } finally { $('#rule-save').disabled = false; }
});

/* ════════════════════════════ Categorisation progress ════════════════════════════ */
async function triggerCategorisation(recategorise = false) {
  await api('/categorise/trigger' + (recategorise ? '?recategorise=true' : ''), { method: 'POST' });
  return watchCategorisation();
}

let watching = null;
function watchCategorisation() {
  if (watching) return watching;
  const banner = $('#progress');
  banner.hidden = false;
  let idle = 0;
  let seenRunning = false;
  watching = new Promise((resolve) => {
    const tick = async () => {
      try {
        const s = await api('/categorise/status');
        const done = s.totalMerchants - s.uncategorisedMerchants;
        $('#progress-text').textContent = `Categorising merchants… ${done} / ${s.totalMerchants}`;
        $('#progress-bar').style.width = (s.totalMerchants ? (done / s.totalMerchants) * 100 : 100) + '%';
        if (s.running) seenRunning = true; else idle++;
        if (!s.running && (seenRunning || idle >= 3)) {
          banner.hidden = true; watching = null;
          if (s.uncategorisedMerchants) {
            toast(`${s.uncategorisedMerchants} merchant(s) couldn't be categorised automatically — use "Fix" to add a rule.`, 6000);
          }
          refreshCurrentView();
          return resolve(s);
        }
      } catch { /* transient — keep polling */ }
      setTimeout(tick, 1200);
    };
    tick();
  });
  return watching;
}

function refreshCurrentView() {
  if (!$('#view-dashboard').hidden) loadDashboard();
  if (!$('#view-transactions').hidden) { state.categories = []; loadTransactionsView(); }
}

/* ════════════════════════════ Global actions ════════════════════════════ */
document.addEventListener('click', async (e) => {
  const a = e.target.closest('[data-action]');
  if (!a) return;
  if (a.dataset.action === 'sample') loadSample();
  if (a.dataset.action === 'recategorise') {
    a.disabled = true;
    try { await triggerCategorisation(true); } catch (err) { toast(err.message); } finally { a.disabled = false; }
  }
  if (a.dataset.action === 'reset') {
    if (!confirm('Delete all imported transactions and their categories? Rules are kept.')) return;
    try {
      await api('/transactions', { method: 'DELETE' });
      state.categories = [];
      toast('All transactions cleared');
      loadDashboard();
    } catch (err) { toast(err.message); }
  }
});

/* ════════════════════════════ Import: parsing ════════════════════════════ */
const MONTHS = { jan: 1, feb: 2, mar: 3, apr: 4, may: 5, jun: 6, jul: 7, aug: 8, sep: 9, sept: 9, oct: 10, nov: 11, dec: 12 };
const pad = (n) => String(n).padStart(2, '0');
const fullYear = (y) => (y < 100 ? 2000 + y : y);

/** Parses statement dates; Indian banks are day-first. Returns yyyy-mm-dd or null. */
function parseDate(v) {
  if (v == null || v === '') return null;
  if (v instanceof Date && !isNaN(v)) return `${v.getFullYear()}-${pad(v.getMonth() + 1)}-${pad(v.getDate())}`;
  if (typeof v === 'number' && v > 20000 && v < 80000) { // Excel serial date
    const d = new Date(Math.round((v - 25569) * 86400000));
    return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`;
  }
  const s = String(v).trim();
  let m;
  const ok = (y, mo, d) => (mo >= 1 && mo <= 12 && d >= 1 && d <= 31 ? `${y}-${pad(mo)}-${pad(d)}` : null);
  if ((m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})/))) return ok(+m[1], +m[2], +m[3]);
  if ((m = s.match(/^(\d{1,2})[/\-. ](\d{1,2})[/\-. ](\d{2,4})\b/))) return ok(fullYear(+m[3]), +m[2], +m[1]);
  if ((m = s.match(/^(\d{1,2})[/\-. ]+([A-Za-z]{3,9})[/\-., ]+(\d{2,4})\b/))) {
    const mo = MONTHS[m[2].toLowerCase()] ?? MONTHS[m[2].slice(0, 3).toLowerCase()];
    return mo ? ok(fullYear(+m[3]), mo, +m[1]) : null;
  }
  if ((m = s.match(/^([A-Za-z]{3,9})[ .]+(\d{1,2}),?[ ]+(\d{4})\b/))) {
    const mo = MONTHS[m[1].slice(0, 3).toLowerCase()];
    return mo ? ok(+m[3], mo, +m[2]) : null;
  }
  return null;
}

/** "1,234.50", "₹ 1,234.50 Dr", "(500.00)" → number (NaN when empty). */
function parseAmount(v) {
  if (v == null || v === '') return NaN;
  if (typeof v === 'number') return v;
  let s = String(v).trim();
  const neg = /^\(.*\)$/.test(s) || /^-/.test(s);
  s = s.replace(/(INR|Rs\.?|₹|Dr|Cr|[,\s()+-])/gi, '');
  const n = parseFloat(s);
  return isNaN(n) ? NaN : neg ? -n : n;
}

const COL_PATTERNS = {
  date: [/^(txn|tran|transaction|posting|value)?\s*date$/i, /txn date|tran date|transaction date|posting date/i, /date/i],
  desc: [/narration|description|particulars|transaction details|details|remarks/i],
  debit: [/withdrawal|debit|\bdr\b|paid out|money out/i],
  credit: [/deposit|credit|\bcr\b|paid in|money in/i],
  amount: [/^amount|amount\s*\(|txn amount|transaction amount/i],
  type: [/dr\s*\/\s*cr|cr\s*\/\s*dr|^type$|transaction type/i],
};

function findHeaderRow(rows) {
  for (let i = 0; i < Math.min(rows.length, 60); i++) {
    const cells = rows[i].map((c) => String(c ?? '').trim());
    if (cells.some((c) => /date/i.test(c)) && cells.some((c) => COL_PATTERNS.desc[0].test(c))) return i;
  }
  return -1;
}

function guessColumns(header) {
  const pick = (key, exclude = []) => {
    for (const re of COL_PATTERNS[key]) {
      // Prefer the transaction date over the value date when both exist.
      const order = key === 'date' ? [...header.keys()].sort((a, b) => /value/i.test(header[a]) - /value/i.test(header[b])) : [...header.keys()];
      const idx = order.find((i) => re.test(header[i]) && !exclude.includes(i));
      if (idx !== undefined) return idx;
    }
    return -1;
  };
  const cols = { date: pick('date'), desc: pick('desc') };
  cols.debit = pick('debit');
  cols.credit = pick('credit', [cols.debit]);
  cols.amount = cols.debit === -1 ? pick('amount') : -1;
  cols.type = pick('type');
  if (cols.type === cols.debit || cols.type === cols.credit) cols.type = -1;
  return cols;
}

/** Turns rows + column mapping into debit transactions, with stats about what was skipped. */
function extractTransactions(rows, cols) {
  const out = []; let credits = 0; let skipped = 0;
  const hasNegatives = cols.amount >= 0 && rows.some((r) => parseAmount(r[cols.amount]) < 0);
  for (const r of rows) {
    const date = parseDate(r[cols.date]);
    const desc = String(r[cols.desc] ?? '').replace(/\s+/g, ' ').trim();
    if (!date || !desc) { if (r.some((c) => String(c ?? '').trim())) skipped++; continue; }
    let debit = 0;
    if (cols.debit >= 0) {
      debit = Math.abs(parseAmount(r[cols.debit])) || 0;
      if (!debit && cols.credit >= 0 && parseAmount(r[cols.credit]) > 0) { credits++; continue; }
    } else if (cols.amount >= 0) {
      const amt = parseAmount(r[cols.amount]);
      const type = cols.type >= 0 ? String(r[cols.type] ?? '').trim().toUpperCase() : '';
      const isCredit = type ? type.startsWith('C') : hasNegatives ? amt > 0 : false;
      if (isCredit) { credits++; continue; }
      debit = Math.abs(amt) || 0;
    }
    if (!debit) { skipped++; continue; }
    out.push({ date, desc: desc.slice(0, 512), debit: Math.round(debit * 100) / 100 });
  }
  return { txns: out, credits, skipped };
}

/* ════════════════════════════ Import: UI ════════════════════════════ */
const imp = { rows: [], header: [], cols: null, fileName: '' };

async function readFile(file) {
  const isExcel = /\.xlsx?$/i.test(file.name);
  if (isExcel) {
    await loadScript('https://cdnjs.cloudflare.com/ajax/libs/xlsx/0.18.5/xlsx.full.min.js');
    const wb = XLSX.read(await file.arrayBuffer(), { type: 'array', cellDates: true });
    return XLSX.utils.sheet_to_json(wb.Sheets[wb.SheetNames[0]], { header: 1, raw: true, defval: '' });
  }
  return new Promise((resolve, reject) => Papa.parse(file, {
    skipEmptyLines: false, complete: (r) => resolve(r.data), error: reject,
  }));
}

function loadScript(src) {
  if (document.querySelector(`script[src="${src}"]`)) return Promise.resolve();
  return new Promise((res, rej) => {
    const s = document.createElement('script');
    s.src = src; s.onload = res; s.onerror = () => rej(new Error('Could not load Excel reader'));
    document.head.appendChild(s);
  });
}

async function handleFile(file) {
  try {
    const all = await readFile(file);
    const h = findHeaderRow(all);
    if (h < 0) throw new Error("Couldn't find a header row with a Date and a Narration/Description column.");
    imp.header = all[h].map((c) => String(c ?? '').trim());
    imp.rows = all.slice(h + 1);
    imp.cols = guessColumns(imp.header);
    imp.fileName = file.name;
    showMapping();
  } catch (e) {
    toast(e.message, 6000);
  }
}

function showMapping() {
  $('#map-file').textContent = imp.fileName;
  $$('[data-col]').forEach((sel) => {
    const key = sel.dataset.col;
    sel.innerHTML = '<option value="-1">— none —</option>' +
      imp.header.map((h, i) => `<option value="${i}" ${imp.cols[key] === i ? 'selected' : ''}>${esc(h || `Column ${i + 1}`)}</option>`).join('');
  });
  $('#mapping').hidden = false;
  updatePreview();
  $('#mapping').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function updatePreview() {
  $$('[data-col]').forEach((sel) => { imp.cols[sel.dataset.col] = Number(sel.value); });
  const c = imp.cols;
  const ready = c.date >= 0 && c.desc >= 0 && (c.debit >= 0 || c.amount >= 0);
  $('#map-import').disabled = !ready;
  if (!ready) {
    $('#map-summary').textContent = 'Pick the Date, Narration and either Debit or Amount columns.';
    $('#preview-body').innerHTML = '';
    return;
  }
  const { txns, credits, skipped } = extractTransactions(imp.rows, c);
  imp.txns = txns;
  const total = txns.reduce((s, t) => s + t.debit, 0);
  state.currency = $('#currency').value;
  $('#map-summary').innerHTML = `<b>${txns.length}</b> debits totalling <b>${money(total)}</b> will be imported.` +
    (credits ? ` ${credits} credit(s) (salary, refunds…) are left out — the analysis is about spending.` : '') +
    (skipped ? ` ${skipped} non-transaction row(s) skipped.` : '');
  $('#preview-body').innerHTML = txns.slice(0, 50).map((t) =>
    `<tr><td style="white-space:nowrap">${t.date}</td><td>${esc(t.desc)}</td><td class="num">${money(t.debit)}</td></tr>`).join('');
}

$('#mapping').addEventListener('change', updatePreview);
$('#map-cancel').addEventListener('click', () => { $('#mapping').hidden = true; $('#file').value = ''; });
$('#map-import').addEventListener('click', async () => {
  const btn = $('#map-import');
  btn.disabled = true; btn.textContent = 'Importing…';
  try {
    await importTransactions(imp.txns, $('#currency').value);
    $('#mapping').hidden = true; $('#file').value = '';
  } catch (e) { toast('Import failed: ' + e.message, 6000); } finally {
    btn.disabled = false; btn.textContent = 'Import & categorise';
  }
});

async function importTransactions(txns, currency) {
  const today = new Date().toISOString().slice(0, 10);
  const payload = txns.map((t) => ({
    merchantName: t.desc, amount: t.debit, currency, transactionDate: t.date > today ? today : t.date, source: 'IMPORT',
  }));
  let accepted = 0; let rejected = 0;
  for (let i = 0; i < payload.length; i += 5000) {
    const r = await api('/transactions/bulk', { method: 'POST', body: payload.slice(i, i + 5000) });
    accepted += r.acceptedCount; rejected += r.rejectedCount;
  }
  state.currency = currency; state.categories = [];
  toast(`Imported ${accepted} transaction(s)${rejected ? `, ${rejected} rejected` : ''}. Categorising…`);
  location.hash = 'dashboard';
  await triggerCategorisation(false);
}

async function loadSample() {
  location.hash = 'import';
  try {
    const res = await fetch('samples/sample-passbook.csv');
    const blob = await res.blob();
    await handleFile(new File([blob], 'sample-passbook.csv', { type: 'text/csv' }));
  } catch (e) { toast('Could not load the sample: ' + e.message); }
}

const drop = $('#drop');
$('#file').addEventListener('change', (e) => { if (e.target.files[0]) handleFile(e.target.files[0]); });
['dragenter', 'dragover'].forEach((ev) => drop.addEventListener(ev, (e) => { e.preventDefault(); drop.classList.add('over'); }));
['dragleave', 'drop'].forEach((ev) => drop.addEventListener(ev, (e) => { e.preventDefault(); drop.classList.remove('over'); }));
drop.addEventListener('drop', (e) => { if (e.dataTransfer.files[0]) handleFile(e.dataTransfer.files[0]); });
$('#currency').addEventListener('change', updatePreview);

/* Re-theme charts when the OS theme flips. */
window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => { if (!$('#view-dashboard').hidden) loadDashboard(); });

route();
