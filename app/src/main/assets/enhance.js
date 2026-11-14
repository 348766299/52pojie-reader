/* 吾爱阅读 · 页面增强核心
 * 目标站点：Discuz! X（52pojie.cn），实测特征：
 *   - GBK 编码、无 CSP、帖子页可匿名访问
 *   - #thread_subject 标题
 *   - div#post_xxxxx 楼层容器，内含 td.t_f / #postmessage_xxx 正文、.authi 作者与时间
 *   - 站点 viewport 写死 user-scalable=no，手机上无法缩放，需修复
 */
(function () {
  'use strict';
  if (window.R52 && window.R52.__v) return;

  var S = {
    mode: 1,      // 0 原版 / 1 优化排版 / 2 沉浸阅读
    scale: 1,     // 正文缩放倍率
    night: false,
    tidy: true,   // 精简页面（隐藏侧栏 / 页脚 / 推广位）
    zoom: true,   // 恢复双指缩放
    nopic: false, // 隐藏图片
    onlyOp: false // 只看楼主
  };

  var R = {};
  window.R52 = R;
  R.__v = 1;

  function q(sel, root) { return (root || document).querySelector(sel); }
  function qa(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }
  function txt(el) { return el ? (el.textContent || '').replace(/\s+/g, ' ').trim() : ''; }
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function bridge(name, arg) {
    try {
      if (window.R52Bridge && typeof window.R52Bridge[name] === 'function') {
        window.R52Bridge[name](arg);
      }
    } catch (e) { /* 页面侧异常不应影响浏览 */ }
  }

  function isThread() {
    return !!q('#thread_subject') && postNodes().length > 0;
  }
  function postNodes() {
    return qa('div[id^="post_"]').filter(function (p) {
      // 页面里还有 post_rate_xxx（评分）与 post_new 等辅助节点，
      // 真实楼层 ID 形如 post_35725867，只认纯数字，避免误收。
      if (!/^post_\d+$/.test(p.id || '')) return false;
      return !!q('td.t_f, div.t_f, [id^="postmessage_"]', p);
    });
  }

  /* 站点禁止缩放，这里改成可缩放并支持 5 倍放大 */
  function fixViewport() {
    var m = q('meta[name="viewport"]');
    if (!m) {
      m = document.createElement('meta');
      m.setAttribute('name', 'viewport');
      (q('head') || document.documentElement).appendChild(m);
    }
    m.setAttribute('content',
      'width=device-width, initial-scale=1.0, minimum-scale=0.5, maximum-scale=5.0, user-scalable=yes, viewport-fit=cover');
  }

  /* Discuz 常把真实图片放在 file / zoomfile 属性里，src 是占位图 */
  function bestUrl(img) {
    return img.getAttribute('file') || img.getAttribute('zoomfile') ||
      img.getAttribute('data-original') || img.getAttribute('data-src') ||
      img.currentSrc || img.getAttribute('src') || '';
  }
  function restoreLazyImages(root) {
    qa('img', root).forEach(function (img) {
      var src = img.getAttribute('src') || '';
      var real = bestUrl(img);
      if (real && (!src || /none\.gif|blank\.gif|placeholder|loading/i.test(src))) {
        img.setAttribute('src', real);
      }
      img.removeAttribute('width');
      img.removeAttribute('height');
      img.removeAttribute('onload');
      img.removeAttribute('onclick');
      img.removeAttribute('onerror');
    });
  }
  function collectImages(scope) {
    var seen = {};
    return qa('img', scope).map(bestUrl).filter(function (u) {
      if (!u || /^data:/.test(u) || /none\.gif|blank\.gif/i.test(u) || seen[u]) return false;
      seen[u] = 1;
      return true;
    });
  }

  /* 站点首页的「更多 New~」、帖子列表标题等链接几乎都带 target="_blank"。
   * 单 WebView 客户端里这类链接必须统一在当前页面打开：既避免多窗口，
   * 也避免点开后没有任何反应。DOM 里清一遍，点击瞬间再兜底一次
   * （覆盖 Discuz AJAX 之后才插入的节点）。 */
  function normalizeTargets(root) {
    qa('a[target]', root).forEach(function (a) {
      var t = (a.getAttribute('target') || '').toLowerCase();
      if (t === '_blank' || t === '_new') a.setAttribute('target', '_self');
    });
  }

  function normalizeClickedLink(target) {
    if (!target || !target.closest) return;
    var a = target.closest('a[target]');
    if (!a) return;
    var t = (a.getAttribute('target') || '').toLowerCase();
    if (t === '_blank' || t === '_new') a.setAttribute('target', '_self');
  }

  var clickBound = false;
  function bindClickOnce() {
    if (clickBound) return;
    clickBound = true;
    document.addEventListener('click', function (e) {
      normalizeClickedLink(e.target);
      var img = e.target;
      if (!img || img.tagName !== 'IMG') return;
      // 带链接的图片（附件、图文跳转）保持原行为
      if (img.closest && img.closest('a')) return;
      var url = bestUrl(img);
      if (!url || /^data:/.test(url)) return;
      var scope = q('#r52-reader-root') || q('#postlist') || document.body;
      var urls = collectImages(scope);
      var idx = urls.indexOf(url);
      if (idx < 0) { urls = [url]; idx = 0; }
      e.preventDefault();
      e.stopPropagation();
      bridge('openImage', JSON.stringify({ urls: urls, index: idx }));
    }, true);
  }

  /* 沉浸阅读：只保留标题 + 每层作者 / 时间 / 正文，重新排成移动端卡片 */
  function buildReader() {
    var title = txt(q('#thread_subject')) || document.title.replace(/\s*-\s*吾爱破解.*$/, '').trim();
    var posts = postNodes();
    var opName = '';
    if (posts.length) {
      opName = txt(q('.authi .xw1', posts[0]) || q('.authi a', posts[0]));
    }

    var h = [];
    h.push('<div id="r52-reader-root" class="r52-root">');
    h.push('<header class="r52-hd"><h1>' + esc(title) + '</h1>' +
      '<div class="r52-sub">' + posts.length + ' 楼 · 沉浸阅读 · 点图片可放大 · 返回原页面可回复</div></header>');

    posts.forEach(function (p, i) {
      var body = q('td.t_f, div.t_f, [id^="postmessage_"]', p);
      if (!body) return;
      var name = txt(q('.authi .xw1', p) || q('.authi a', p)) || '匿名';
      var tm = txt(q('.authi em', p) || q('[id^="authorposton"]', p));
      var isOp = opName && name === opName;
      h.push('<article class="r52-post' + (isOp ? ' r52-op' : '') + '">');
      h.push('<div class="r52-ph"><span class="r52-name">' + esc(name) + '</span>' +
        (isOp ? '<span class="r52-badge">楼主</span>' : '') +
        '<span class="r52-floor">' + (i + 1) + ' 楼</span>' +
        (tm ? '<span class="r52-time">' + esc(tm) + '</span>' : '') + '</div>');
      h.push('<div class="r52-body">' + body.innerHTML + '</div>');
      h.push('</article>');
    });

    h.push('<div class="r52-end">— 已经到底了 —</div></div>');

    var box = document.createElement('div');
    box.innerHTML = h.join('');
    var node = box.firstChild;

    // 复制出来的 DOM 里去掉脚本和内联事件，避免干扰阅读
    qa('script, iframe[src^="javascript:"]', node).forEach(function (n) {
      if (n.parentNode) n.parentNode.removeChild(n);
    });
    qa('[onclick], [onload], [onerror], [onmouseover]', node).forEach(function (n) {
      n.removeAttribute('onclick'); n.removeAttribute('onload');
      n.removeAttribute('onerror'); n.removeAttribute('onmouseover');
    });
    restoreLazyImages(node);
    document.body.appendChild(node);
  }

  function report() {
    bridge('onPageInfo', JSON.stringify({
      thread: isThread(),
      posts: postNodes().length,
      title: txt(q('#thread_subject')) || document.title,
      mode: S.mode,
      onlyOp: S.onlyOp
    }));
  }

  R.setOptions = function (o) {
    if (!o) return;
    ['mode', 'scale', 'night', 'tidy', 'zoom', 'nopic', 'onlyOp'].forEach(function (k) {
      if (Object.prototype.hasOwnProperty.call(o, k) && o[k] !== undefined && o[k] !== null) {
        S[k] = o[k];
      }
    });
  };

  R.apply = function () {
    var root = document.documentElement;
    var old = q('#r52-reader-root');
    if (old && old.parentNode) old.parentNode.removeChild(old);

    root.classList.remove('r52-opt', 'r52-tidy', 'r52-reader', 'r52-night', 'r52-nopic', 'r52-onlyop');
    try { root.style.setProperty('--r52-scale', String(S.scale)); } catch (e) { /* ignore */ }

    if (S.zoom) fixViewport();

    if (S.mode === 2 && isThread()) {
      buildReader();
      root.classList.add('r52-reader');
    } else if (S.mode === 1) {
      root.classList.add('r52-opt');
      if (S.tidy) root.classList.add('r52-tidy');
    }

    if (S.night) root.classList.add('r52-night');
    if (S.nopic) root.classList.add('r52-nopic');
    if (S.onlyOp) root.classList.add('r52-onlyop');

    // 无条件执行：即使切到「原版」模式，也不能让 _blank 链接触发多窗口
    normalizeTargets(document);
  };

  R.install = function (cssB64, opts) {
    var css = window.__R52_DEC__ ? window.__R52_DEC__(cssB64) : '';
    var st = q('#r52-style');
    if (!st) {
      st = document.createElement('style');
      st.id = 'r52-style';
      (q('head') || document.documentElement).appendChild(st);
    }
    st.textContent = css;
    R.setOptions(opts);
    R.apply();
    bindClickOnce();
    report();
  };

  R.applyOptions = function (opts) {
    R.setOptions(opts);
    R.apply();
    report();
    return 'ok';
  };

  /* 提取正文里的网盘 / 附件下载链接 */
  R.extractLinks = function () {
    var KEYS = [
      'pan.baidu.com', 'yun.baidu.com', 'lanzou', 'lanzoux', 'lanzoui', 'wwp.',
      'ctfile', '123pan', 'quark.cn', 'pan.quark', 'aliyundrive', 'alipan.com',
      'pan.xunlei', 'cowtransfer', 'c-t.work', 'down.52pojie.cn',
      'mod=attachment', 'attachment/', 'pan.52pojie', '123684', 'sharepoint'
    ];
    var scope = q('#r52-reader-root') || q('#postlist') || document.body;
    var out = [], seen = {};
    qa('a[href]', scope).forEach(function (a) {
      var href = a.getAttribute('href') || '';
      if (!href || /^(javascript:|#|mailto:)/i.test(href)) return;
      var abs = a.href || href;
      var hit = KEYS.some(function (k) { return abs.toLowerCase().indexOf(k) !== -1; });
      if (!hit || seen[abs]) return;
      seen[abs] = 1;
      out.push({ url: abs, text: (txt(a) || abs).slice(0, 90) });
    });
    var json = JSON.stringify(out);
    bridge('onLinks', json);
    return json;
  };

  /* 返回顶部 / 滚动到指定楼层（供原生侧调用） */
  R.scrollTo = function (y) {
    try { window.scrollTo(0, Number(y) || 0); } catch (e) { /* ignore */ }
  };

  R.state = function () { return S; };
})();
