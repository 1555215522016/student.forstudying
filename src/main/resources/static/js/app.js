/* ============ 西电校园 · 前端逻辑 ============ */
'use strict';

const $ = s => document.querySelector(s);

const state = {
  user: null,
  view: 'share',
  sort: 'latest',          // 帖子排序：latest/oldest/likes
  sharePage: 1,
  shareKw: '',
  coursePage: 1,
  courseKw: { className: '', teacherName: '' },
  onlyMine: false,
  detailPostId: null,
  pendingImgs: [],
  pendingAvatar: null,
  profileUserId: null,
};

/* ---------- 基础工具 ---------- */
function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : ''; }

async function api(path, options = {}) {
  const opts = { ...options, headers: {} };
  if (options.body) opts.headers['Content-Type'] = 'application/json';
  const res = await fetch(path, opts);
  const body = await res.json().catch(() => ({ status: -1, msg: '网络异常，请稍后重试' }));
  if (!body || body.status !== 0) {
    const err = new Error(body && body.msg ? body.msg : '请求失败');
    err.status = body && body.status;
    throw err;
  }
  return body.data;
}

/* ---------- 高级通知（右上角滑入 + 进度条 + 图标） ---------- */
function notify(type, title, sub) {
  const holder = $('#toast-holder');
  const icon = { success: '✓', error: '✕', info: '·' }[type] || '·';
  const el = document.createElement('div');
  el.className = 'n-item n-' + (type || 'info');
  el.innerHTML =
    '<span class="n-icon">' + icon + '</span>' +
    '<div class="n-body">' +
    '<div class="n-title">' + esc(title || '') + '</div>' +
    (sub ? '<div class="n-sub">' + esc(sub) + '</div>' : '') +
    '</div>' +
    '<span class="n-bar"></span>';
  holder.appendChild(el);
  requestAnimationFrame(() => el.classList.add('n-show'));
  const duration = type === 'success' ? 3000 : 3600;
  const bar = el.querySelector('.n-bar');
  bar.style.transitionDuration = duration + 'ms';
  requestAnimationFrame(() => { bar.style.width = '0'; });
  setTimeout(() => {
    el.classList.remove('n-show');
    el.classList.add('n-hide');
    setTimeout(() => el.remove(), 380);
  }, duration);
}

function toast(msg, type, sub) {
  notify(type || 'error', msg, sub);
}

function renderPager(el, page, size, total, onGo) {
  const pages = Math.max(1, Math.ceil(total / size));
  el.innerHTML =
    '<button class="btn btn-ghost" id="pg-prev">上一页</button>' +
    '<span>第 ' + page + ' / ' + pages + ' 页（共 ' + total + ' 条）</span>' +
    '<button class="btn btn-ghost" id="pg-next">下一页</button>';
  el.querySelector('#pg-prev').onclick = () => { if (page > 1) onGo(page - 1); };
  el.querySelector('#pg-next').onclick = () => { if (page < pages) onGo(page + 1); };
}

/* ---------- 登录 / 登出 / 启动 ---------- */
function showLogin() {
  state.user = null;
  $('#topbar').hidden = true;
  $('#app-shell').hidden = true;
  $('#view-login').hidden = false;
  $('#detail-mask').hidden = true;
}

function updateNav() {
  const u = state.user || {};
  $('#user-name').textContent = (u.nickname || u.studentId || '');
  $('#user-badge').hidden = !(u.role === 1);
  // 顶栏头像：有图显图，没图显示首字符占位（始终有可点的头像位）
  const na = $('#nav-avatar');
  na.outerHTML = u.avatarUrl
    ? '<img class="avatar avatar-nav" id="nav-avatar" src="' + esc(u.avatarUrl) + '" alt="头像" onclick="openProfile(' + u.userId + ')">'
    : '<span class="avatar avatar-nav avatar-fallback" id="nav-avatar" onclick="openProfile(' + u.userId + ')">' + esc((u.nickname || u.studentId || '?').charAt(0)) + '</span>';
}

function enterApp() {
  state.user = state.user || {};
  $('#topbar').hidden = false;
  $('#app-shell').hidden = false;
  $('#view-login').hidden = true;
  updateNav();
  navigate('share');
}

$('#login-form').onsubmit = async e => {
  e.preventDefault();
  const studentId = $('#input-sid').value.trim();
  const password = $('#input-pwd').value;
  $('#login-err').textContent = '';
  try {
    state.user = await api('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ studentId, password }),
    });
    enterApp();
  } catch (err) {
    $('#login-err').textContent = err.message || '登录失败';
  }
};

$('#btn-logout').onclick = async () => {
  try { await api('/api/v1/auth/logout', { method: 'POST' }); } catch (e) { /* 忽略 */ }
  showLogin();
};

async function init() {
  try {
    state.user = await api('/api/v1/auth/me');
    enterApp();
  } catch (e) {
    showLogin();
  }
}

/* ---------- 导航 ---------- */
function navigate(view) {
  state.view = view;
  document.querySelectorAll('#tabs .tab').forEach(t => t.classList.toggle('active', t.dataset.view === view));
  document.querySelectorAll('#app-shell .view').forEach(v => { v.hidden = v.id !== 'view-' + view; });
  if (view === 'share') loadShare(false);
  else if (view === 'course') loadCourse(false);
}
$('#tabs').addEventListener('click', e => {
  const tab = e.target.closest('.tab');
  if (tab) navigate(tab.dataset.view);
});

/* ---------- 生活分享 ---------- */
/* ---------- 发帖：图片上传（OSS 预签名直传） ---------- */
async function uploadImg(file) {
  const dto = await api('/api/v1/oss/presign?fileName=' + encodeURIComponent(file.name), { method: 'POST' });
  // 【部署·地址自适配】旧版直接用后端返回的地址（localhost:9000）——
  /*  远程用户的浏览器拿到后会去连他自己的电脑，必挂。 */
  //   const res = await fetch(dto.presignedUrl, { method: 'PUT', body: file });
  //   if (!res.ok) throw new Error('图片上传失败');
  //   // bucket 是公开读：存"不带签名"的公开 URL（短、永不过期）
  //   return dto.objectUrl.split('?')[0];
  // ↓ 新版：把 localhost:9000 改写为"页面当前域名:9000"（MinIO 与后端同机，对外即服务器地址）
  const fix = u => u.replace(/^(https?:\/\/)(localhost|127\.0\.0\.1):9000/, '$1' + location.hostname + ':9000');
  const res = await fetch(fix(dto.presignedUrl), { method: 'PUT', body: file });
  if (!res.ok) throw new Error('图片上传失败');
  return fix(dto.objectUrl).split('?')[0];
}

function renderPendingImgs() {
  const box = $('#post-imgs');
  box.innerHTML = state.pendingImgs.map((u, i) =>
    '<span class="img-preview"><img src="' + esc(u) + '">' +
    '<button class="img-del" data-i="' + i + '" title="移除">✕</button></span>').join('');
  box.querySelectorAll('.img-del').forEach(b => {
    b.onclick = () => { state.pendingImgs.splice(Number(b.dataset.i), 1); renderPendingImgs(); };
  });
}

$('#btn-pick-img').onclick = () => $('#post-images').click();
$('#post-images').onchange = async e => {
  const files = Array.from(e.target.files || []);
  e.target.value = '';
  for (const f of files) {
    try {
      const url = await uploadImg(f);
      state.pendingImgs.push(url);
      renderPendingImgs();
    } catch (err) { toast(err.message); }
  }
};

$('#btn-post').onclick = async () => {
  const content = $('#post-content').value.trim();
  if (!content) return toast('内容不能为空');
  const btn = $('#btn-post');
  btn.disabled = true;
  try {
    await api('/api/v1/posts', {
      method: 'POST',
      body: JSON.stringify({ content, isAnonymous: $('#post-anon').checked, mediaUrls: state.pendingImgs }),
    });
    state.pendingImgs = [];
    $('#post-content').value = '';
    $('#post-anon').checked = false;
    renderPendingImgs();
    toast('发布成功', 'success', '帖子已发布到「生活分享」');
    loadShare(true);
  } catch (err) { toast(err.message); }
  finally { btn.disabled = false; }
};

$('#search-form').onsubmit = e => {
  e.preventDefault();
  state.shareKw = $('#search-kw').value.trim();
  state.sharePage = 1;
  loadShare(false);
};

/* 帖子排序切换 */
$('#post-sortbar').addEventListener('click', e => {
  const seg = e.target.closest('.seg');
  if (!seg) return;
  state.sort = seg.dataset.sort;
  document.querySelectorAll('#post-sortbar .seg').forEach(s => s.classList.toggle('active', s === seg));
  state.sharePage = 1;
  loadShare(false);
});

async function loadShare(refresh) {
  if (refresh) state.sharePage = 1;
  const box = $('#posts-list');
  box.innerHTML = '<p class="empty">加载中…</p>';
  try {
    if (state.shareKw) {
      /* 搜索走 ES，返回裸 List<PostDocument>（无昵称/点赞数） */
      const data = await api('/api/v1/posts/search?keyword=' + encodeURIComponent(state.shareKw) +
        '&page=' + state.sharePage + '&size=10');
      const arr = Array.isArray(data) ? data : [];
      box.innerHTML = arr.length
        ? arr.map(postDocCard).join('')
        : '<p class="empty">没有搜到相关帖子</p>';
      $('#share-pager').innerHTML = '';
    } else {
      const data = await api('/api/v1/posts?page=' + state.sharePage + '&size=10&sort=' + state.sort);
      const list = data.list || [];
      box.innerHTML = list.length
        ? list.map(postCard).join('')
        : '<p class="empty">还没有帖子，来发第一帖吧～</p>';
      renderPager($('#share-pager'), state.sharePage, 10, data.total || 0,
        p => { state.sharePage = p; loadShare(false); });
    }
  } catch (err) { box.innerHTML = '<p class="empty">' + err.message + '</p>'; }
}

function postCard(p) {
  const name = p.anonymous
    ? '<span class="post-name anon">匿名用户</span>'
    : '<a class="post-name" href="javascript:void(0)" onclick="openProfile(' + p.userId + ')">' + esc(p.nickname || '同学') + '</a>';
  const avatar = avatarHtml(p.avatarUrl, p.nickname, 'avatar-card', p.anonymous ? null : p.userId);
  const c0 = p.likeCount || 0, c1 = p.dislikeCount || 0, c2 = p.commentCount || 0;
  // 本人 或 管理员 可见"删除"按钮（后端 delete 已校验 isOwner||isAdmin）
  const mine = state.user && (p.userId === state.user.userId || state.user.role === 1);
  return '<div class="card post">' +
    '<div class="post-head"><span class="post-author">' + avatar + name + '</span>' +
    '<span class="post-time">' + fmt(p.createdAt) + '</span></div>' +
    '<div class="post-content">' + esc(p.content) + '</div>' +
    '<div class="post-stats">👍 ' + c0 + ' ｜ 👎 ' + c1 + ' ｜ 💬 ' + c2 + '</div>' +
    '<div class="post-actions">' +
    '<button class="btn btn-ghost' + (p.reactionStatus === 1 ? ' act-liked' : '') + '" onclick="react(' + p.id + ",'like')" + '">赞 (' + c0 + ')</button>' +
    '<button class="btn btn-ghost' + (p.reactionStatus === 2 ? ' act-liked' : '') + '" onclick="react(' + p.id + ",'dislike')" + '">踩 (' + c1 + ')</button>' +
    '<button class="btn btn-ghost" onclick="openDetail(' + p.id + ')">评论 (' + c2 + ')</button>' +
    (mine ? '<button class="btn btn-ghost" onclick="deletePost(' + p.id + ')">删除</button>' : '') +
    '</div></div>';
}

function postDocCard(d) {
  const name = '<a class="post-name" href="javascript:void(0)" onclick="openProfile(' + d.userid + ')">' + esc('用户 ' + d.userid) + '</a>';
  const avatar = avatarHtml(null, null, 'avatar-card', d.userid);
  return '<div class="card post">' +
    '<div class="post-head"><span class="post-author">' + avatar + name + '</span><span class="post-time">' + fmt(d.createdAt) + '</span></div>' +
    '<div class="post-content">' + esc(d.content) + '</div>' +
    '<div class="post-actions"><button class="btn btn-primary" onclick="openDetail(' + d.id + ')">查看详情</button></div>' +
    '</div>';
}

async function react(id, type) {
  try {
    await api('/api/v1/posts/' + id + '/' + type, { method: 'POST' });
    if ($('#detail-mask').hidden) loadShare(false);
    else openDetail(id);
  } catch (err) { toast(err.message); }
}

/* 删帖：本人或管理员（后端已校验 isOwner||isAdmin，这里只负责前端入口） */
async function deletePost(id) {
  if (!confirm('确定删除这条帖子吗？删除后不可恢复。')) return;
  try {
    await api('/api/v1/posts/' + id, { method: 'DELETE' });
    toast('删除成功', 'success', '帖子已删除');
    if (!$('#detail-mask').hidden) $('#detail-mask').hidden = true;
    loadShare(false);
  } catch (err) { toast(err.message, 'error'); }
}

async function openDetail(id) {
  state.detailPostId = id;
  $('#detail-mask').hidden = false;
  $('#detail-body').innerHTML = '<p class="empty">加载中…</p>';
  $('#comments-list').innerHTML = '';
  try {
    const p = await api('/api/v1/posts/' + id);
    const c0 = p.likeCount || 0, c1 = p.dislikeCount || 0;
    const mine = state.user && (p.userId === state.user.userId || state.user.role === 1);
    let media = '';
    if (p.mediaUrls && p.mediaUrls.length) {
      media = '<div class="detail-media">' + p.mediaUrls.map(u =>
        '<img src="' + esc(u) + '" loading="lazy" style="max-width:100%;border-radius:8px;margin:6px 0">').join('') + '</div>';
    }
    $('#detail-body').innerHTML =
      '<div class="post-head"><span class="post-author">' + avatarHtml(p.avatarUrl, p.nickname, 'avatar-card', p.anonymous ? null : p.userId) +
      (p.anonymous
        ? '<span class="post-name anon">匿名用户</span>'
        : '<a class="post-name" href="javascript:void(0)" onclick="openProfile(' + p.userId + ')">' + esc(p.nickname || '同学') + '</a>') +
      '</span><span class="post-time">' + fmt(p.createdAt) + '</span></div>' +
      '<p class="detail-body">' + esc(p.content) + '</p>' + media +
      '<div class="post-actions">' +
      '<button class="btn btn-ghost' + (p.reactionStatus === 1 ? ' act-liked' : '') + '" onclick="react(' + id + ",'like')" + '">赞 (' + c0 + ')</button>' +
      '<button class="btn btn-ghost' + (p.reactionStatus === 2 ? ' act-liked' : '') + '" onclick="react(' + id + ",'dislike')" + '">踩 (' + c1 + ')</button>' +
      (mine ? '<button class="btn btn-ghost" onclick="deletePost(' + id + ')">删除</button>' : '') +
      '</div>';
    await loadComments(id);
  } catch (err) {
    $('#detail-body').innerHTML = '<p class="empty">' + err.message + '</p>';
  }
}

async function loadComments(id) {
  try {
    const data = await api('/api/v1/posts/' + id + '/comments?page=1&size=50');
    const arr = data.list || [];
    $('#comments-list').innerHTML = arr.length
      ? arr.map(c => {
        const link = c.anonymous ? '' : ' onclick="openProfile(' + c.userId + ')"';
        const name = '<span class="c-name' + (c.anonymous ? ' anon' : '') + '"' + link + '>' + esc(c.nickname || '同学') + '</span>';
        return '<div class="comment">' + avatarHtml(c.avatarUrl, c.nickname, 'avatar-card', c.anonymous ? null : c.userId) +
          '<div class="c-main">' + name + '<span class="c-time">' + fmt(c.createdAt) + '</span>' +
          '<div class="c-body">' + esc(c.content) + '</div></div></div>';
      }).join('')
      : '<p class="empty">还没有评论，来抢沙发～</p>';
  } catch (err) {
    $('#comments-list').innerHTML = '<p class="empty">' + err.message + '</p>';
  }
}

$('#btn-comment').onclick = async () => {
  const content = $('#comment-content').value.trim();
  if (!content) return toast('评论不能为空');
  try {
    await api('/api/v1/posts/' + state.detailPostId + '/comments', {
      method: 'POST',
      body: JSON.stringify({ content, isAnonymous: false }),
    });
    $('#comment-content').value = '';
    await loadComments(state.detailPostId);
    if ($('#detail-mask').hidden) loadShare(false);
  } catch (err) { toast(err.message); }
};

$('#detail-close').onclick = () => { $('#detail-mask').hidden = true; };
$('#detail-mask').onclick = e => { if (e.target.id === 'detail-mask') $('#detail-mask').hidden = true; };

/* ---------- 抢课 ---------- */
$('#course-search-form').onsubmit = e => {
  e.preventDefault();
  state.courseKw = { className: $('#kw-name').value.trim(), teacherName: $('#kw-teacher').value.trim() };
  state.onlyMine = $('#course-only-mine').checked;
  state.coursePage = 1;
  loadCourse(false);
};
$('#course-only-mine').onchange = () => {
  state.onlyMine = $('#course-only-mine').checked;
  state.coursePage = 1;
  loadCourse(false);
};

async function loadCourse(refresh) {
  if (refresh) state.coursePage = 1;
  const box = $('#courses-list');
  box.innerHTML = '<p class="empty">加载中…</p>';
  const qs = new URLSearchParams({ page: state.coursePage, size: 10 });
  if (state.courseKw.className) qs.set('className', state.courseKw.className);
  if (state.courseKw.teacherName) qs.set('teacherName', state.courseKw.teacherName);
  try {
    const data = await api('/api/v1/course?' + qs);
    let items = data.list || [];
    const total = data.total || 0;
    if (state.onlyMine) items = items.filter(c => c.ifchoosen);
    box.innerHTML = items.length
      ? '<div class="courses">' + items.map(courseCard).join('') + '</div>'
      : '<p class="empty">' + (state.onlyMine ? '你还没有抢到课' : '暂无课程') + '</p>';
    items.forEach(c => {
      const el = document.getElementById('cb-' + c.id);
      if (el) el.onclick = () => (c.ifchoosen ? dropCourse(c.id) : chooseCourse(c.id));
    });
    renderPager($('#course-pager'), state.coursePage, 10, total,
      p => { state.coursePage = p; loadCourse(false); });
  } catch (err) { box.innerHTML = '<p class="empty">' + err.message + '</p>'; }
}

function courseCard(c) {
  const full = c.selectedCount != null && c.selectedCount >= 0 && c.selectedCount >= c.capacity;
  const selected = c.selectedCount == null || c.selectedCount < 0 ? '统计中' : c.selectedCount;
  let action;
  if (c.ifchoosen) action = '<button class="btn btn-ghost" id="cb-' + c.id + '">退课</button>';
  else if (full) action = '<button class="btn btn-primary" disabled>已满</button>';
  else action = '<button class="btn btn-primary" id="cb-' + c.id + '">抢课</button>';
  const slot = c.ifchoosen
    ? '<span class="slot mine">✓ 已抢</span>'
    : (full ? '<span class="slot full">已满</span>' : '<span class="slot open">可选</span>');
  return '<div class="card course">' +
    '<div class="course-name">' + esc(c.name) + '</div>' +
    '<div class="course-meta">' +
    '<span>教师：' + esc(c.teacher) + '</span>' +
    '<span>上课时间：' + esc(c.classTime || '—') + '</span>' +
    '<span>容量：' + esc(c.capacity) + ' ｜ 已选：' + esc(selected) + '</span>' +
    '</div>' +
    '<div class="course-foot">' + slot + action + '</div>' +
    '</div>';
}

async function chooseCourse(id) {
  try {
    const data = await api('/api/v1/course', { method: 'POST', body: JSON.stringify({ courseID: id }) });
    if (data && data.status === 1) toast('选课成功', 'success', '名额已锁定 · 已加入你的课表');
    else toast((data && data.message) || '正在处理', 'info', '请稍后刷新查看结果');
    loadCourse(false);
  } catch (err) { toast(err.message, 'error'); }
}

async function dropCourse(id) {
  try {
    const data = await api('/api/v1/course/delete/' + id, { method: 'POST' });
    const msg = (data && data.message) || '退课成功';
    toast(msg, msg.includes('成功') ? 'success' : 'info', '名额已释放');
    loadCourse(false);
  } catch (err) { toast(err.message, 'error'); }
}

/* ---------- 头像 & 个人详情 ---------- */
function avatarHtml(url, name, cls, userId) {
  const on = userId ? ' onclick="openProfile(' + userId + ')"' : '';
  if (url) return '<img class="avatar ' + cls + '" src="' + esc(url) + '" alt="头像"' + on + '>';
  return '<span class="avatar ' + cls + ' avatar-fallback"' + on + '>' + esc((name || '?').charAt(0)) + '</span>';
}

async function openProfile(userId) {
  state.profileUserId = userId;
  $('#profile-mask').hidden = false;
  $('#profile-body').innerHTML = '<p class="empty">加载中…</p>';
  try {
    const p = await api('/api/v1/users/' + userId + '/profile');
    renderProfile(p);
  } catch (err) {
    $('#profile-body').innerHTML = '<p class="empty">' + err.message + '</p>';
  }
}

function pfRow(label, valueHtml) {
  return '<div class="pf-row"><span class="pf-label">' + label + '</span>' + valueHtml + '</div>';
}

function renderProfile(p) {
  const own = !!(state.user && p.userId === state.user.userId);
  const avatar = p.avatarUrl
    ? '<img class="avatar avatar-lg" id="pf-avatar-img" src="' + esc(p.avatarUrl) + '" alt="头像">'
    : '<span class="avatar avatar-lg avatar-fallback" id="pf-avatar-img">' + esc((p.nickname || '?').charAt(0)) + '</span>';
  const nick = own
    ? '<span class="pf-edit"><input id="pf-nick" value="' + esc(p.nickname || '') + '" maxlength="20"></span>'
    : '<span class="pf-value">' + esc(p.nickname || '—') + '</span>';
  const phone = own
    ? '<span class="pf-edit"><input id="pf-phone" value="' + esc(p.phone || '') + '" maxlength="11"></span>'
    : '<span class="pf-value">' + esc(p.phone || '—') + '</span>';
  const actions = own
    ? '<div class="pf-actions">' +
      '<button class="btn btn-ghost" id="pf-pick">📷 上传头像</button>' +
      '<button class="btn btn-primary" id="pf-save">保存</button>' +
      '<input type="file" id="avatar-file" accept="image/*" hidden></div>'
    : '';
  $('#profile-body').innerHTML =
    '<div class="pf-avatar-wrap">' + avatar + '</div>' +
    pfRow('昵称', nick) +
    pfRow('姓名', '<span class="pf-value">' + esc(p.name || '—') + '</span>') +
    pfRow('性别', '<span class="pf-value">' + esc(p.gender || '未知') + '</span>') +
    pfRow('生日', '<span class="pf-value">' + esc(p.birthday || '—') + '</span>') +
    pfRow('手机号', phone) +
    pfRow('专业', '<span class="pf-value">' + esc(p.major || '—') + '</span>') +
    actions;
  if (own) {
    $('#pf-pick').onclick = () => $('#avatar-file').click();
    $('#avatar-file').onchange = async e => {
      const f = e.target.files && e.target.files[0];
      e.target.value = '';
      if (!f) return;
      try {
        const url = await uploadImg(f);
        state.pendingAvatar = url;
        $('#pf-avatar-img').outerHTML = '<img class="avatar avatar-lg" id="pf-avatar-img" src="' + esc(url) + '" alt="头像">';
      } catch (err) { toast(err.message); }
    };
    $('#pf-save').onclick = saveProfile;
  }
}

async function saveProfile() {
  try {
    const body = { nickname: $('#pf-nick').value, phone: $('#pf-phone').value };
    if (state.pendingAvatar) body.avatarUrl = state.pendingAvatar;
    await api('/api/v1/users/' + state.profileUserId + '/profile', { method: 'PUT', body: JSON.stringify(body) });
    state.pendingAvatar = null;
    toast('保存成功', 'success', '个人资料已更新');
    await refreshSelf();
    await openProfile(state.profileUserId);
    loadShare(false);
  } catch (err) { toast(err.message); }
}

async function refreshSelf() {
  try {
    state.user = await api('/api/v1/auth/me');
    updateNav();
  } catch (e) { /* 会话失效等异常交给调用方 */ }
}

$('#profile-close').onclick = () => { $('#profile-mask').hidden = true; };
$('#profile-mask').onclick = e => { if (e.target.id === 'profile-mask') $('#profile-mask').hidden = true; };

init();