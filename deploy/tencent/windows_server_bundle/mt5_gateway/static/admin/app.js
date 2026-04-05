const componentGrid = document.getElementById('componentGrid');
const gatewayStateBox = document.getElementById('gatewayStateBox');
const logsBox = document.getElementById('logsBox');
const envEditor = document.getElementById('envEditor');
const abnormalEditor = document.getElementById('abnormalEditor');
const globalNotice = document.getElementById('globalNotice');

// 统一发请求并处理错误。
async function requestJson(url, options = {}) {
    const response = await fetch(url, options);
    const text = await response.text();
    let payload = {};
    try {
        payload = text ? JSON.parse(text) : {};
    } catch (error) {
        payload = { raw: text };
    }
    if (!response.ok) {
        const message = payload.detail ? JSON.stringify(payload.detail, null, 2) : text || response.statusText;
        throw new Error(message);
    }
    return payload;
}

// 统一设置顶部提示。
function setNotice(message, isError = false) {
    globalNotice.textContent = message;
    globalNotice.style.color = isError ? '#a33f2f' : '#71624e';
}

// 渲染组件卡片。
function renderComponents(components) {
    const entries = Object.entries(components || {});
    componentGrid.innerHTML = '';
    entries.forEach(([key, component]) => {
        const state = component.state || {};
        const actions = component.actions || {};
        const card = document.createElement('article');
        card.className = 'panel card';
        const detail = state.details ? JSON.stringify(state.details, null, 2) : '';
        card.innerHTML = `
            <div class="card-top">
                <div class="card-title">${component.label || key}</div>
                <span class="badge ${state.running ? 'running' : 'stopped'}">${state.statusText || '未知'}</span>
            </div>
            <div class="card-detail">${detail ? detail.replace(/</g, '&lt;') : '暂无详细信息'}</div>
            <div class="card-actions">
                <button class="secondary-btn" data-target="${key}" data-action="start" ${actions.start ? '' : 'disabled'}>启动</button>
                <button class="secondary-btn" data-target="${key}" data-action="stop" ${actions.stop ? '' : 'disabled'}>停止</button>
                <button class="primary-btn" data-target="${key}" data-action="restart" ${actions.restart ? '' : 'disabled'}>重启</button>
            </div>
        `;
        componentGrid.appendChild(card);
    });
}

// 刷新状态区。
async function loadState() {
    const payload = await requestJson('/api/state');
    renderComponents(payload.components || {});
    gatewayStateBox.textContent = JSON.stringify({
        gatewayUrl: payload.gatewayUrl,
        gatewayHealth: payload.gatewayHealth,
        gatewaySource: payload.gatewaySource
    }, null, 2);
}

// 刷新日志区。
async function loadLogs() {
    const payload = await requestJson('/api/logs?limit=200');
    const text = (payload.entries || []).map((item) => `[${item.file}] ${item.line}`).join('\n');
    logsBox.textContent = text || '暂无日志';
}

// 读取当前 .env。
async function loadEnv() {
    const payload = await requestJson('/api/env');
    envEditor.value = payload.content || '';
}

// 读取当前异常规则配置。
async function loadAbnormalConfig() {
    const payload = await requestJson('/api/abnormal-config');
    abnormalEditor.value = JSON.stringify(payload, null, 2);
}

// 保存 .env。
async function saveEnv() {
    await requestJson('/api/env', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content: envEditor.value })
    });
    setNotice('.env 已保存');
}

// 保存异常规则。
async function saveAbnormalConfig() {
    const payload = JSON.parse(abnormalEditor.value || '{}');
    await requestJson('/api/abnormal-config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    setNotice('异常规则已保存');
}

// 清理网关运行时缓存。
async function clearCache() {
    const payload = await requestJson('/api/cache/clear', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({})
    });
    setNotice(`缓存已清理：${JSON.stringify(payload.cleared || {})}`);
    await loadState();
}

// 执行组件管理动作。
async function executeProcessAction(target, action) {
    await requestJson('/api/process', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ target, action })
    });
    setNotice(`${target} 已执行 ${action}`);
    await loadState();
}

// 一次性刷新全部区块。
async function refreshAll() {
    await Promise.all([loadState(), loadLogs(), loadEnv(), loadAbnormalConfig()]);
}

document.getElementById('refreshAllBtn').addEventListener('click', async () => {
    try {
        await refreshAll();
        setNotice('状态已刷新');
    } catch (error) {
        setNotice(`刷新失败：${error.message}`, true);
    }
});

document.getElementById('refreshStateBtn').addEventListener('click', async () => {
    try {
        await loadState();
        setNotice('网关状态已刷新');
    } catch (error) {
        setNotice(`刷新网关状态失败：${error.message}`, true);
    }
});

document.getElementById('refreshLogsBtn').addEventListener('click', async () => {
    try {
        await loadLogs();
        setNotice('日志已刷新');
    } catch (error) {
        setNotice(`刷新日志失败：${error.message}`, true);
    }
});

document.getElementById('saveEnvBtn').addEventListener('click', async () => {
    try {
        await saveEnv();
    } catch (error) {
        setNotice(`保存 .env 失败：${error.message}`, true);
    }
});

document.getElementById('refreshAbnormalBtn').addEventListener('click', async () => {
    try {
        await loadAbnormalConfig();
        setNotice('异常规则已刷新');
    } catch (error) {
        setNotice(`刷新异常规则失败：${error.message}`, true);
    }
});

document.getElementById('saveAbnormalBtn').addEventListener('click', async () => {
    try {
        await saveAbnormalConfig();
    } catch (error) {
        setNotice(`保存异常规则失败：${error.message}`, true);
    }
});

document.getElementById('clearCacheBtn').addEventListener('click', async () => {
    try {
        await clearCache();
    } catch (error) {
        setNotice(`清缓存失败：${error.message}`, true);
    }
});

componentGrid.addEventListener('click', async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) {
        return;
    }
    const action = target.dataset.action;
    const component = target.dataset.target;
    if (!action || !component) {
        return;
    }
    try {
        await executeProcessAction(component, action);
    } catch (error) {
        setNotice(`执行 ${component}/${action} 失败：${error.message}`, true);
    }
});

refreshAll()
    .then(() => setNotice('首次加载完成'))
    .catch((error) => setNotice(`首次加载失败：${error.message}`, true));
