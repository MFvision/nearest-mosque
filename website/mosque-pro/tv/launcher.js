// The TV's apps for the Mosque Pro home screen: listing and opening them, the TV's settings, and Back.
//  - LG webOS: the TV's service bus (Luna), as LG's own launcher does. Developer Mode apps on webOS 6 and newer may
//    be refused the list; then the apps the TV says it has, from a short list of common ones, are shown.
//  - Samsung (Tizen) app: tizen.application lists the apps the TV shows and opens them; Back on the home screen
//    closes it.
//  - Any other browser: sample apps, so the screen can be tried on a computer.
// Plain script (no modules) so it loads the same in a TV app and on the web; it defines window.TVApps.
(function () {
  'use strict';
  var webos = typeof window.PalmServiceBridge !== 'undefined';
  // The Samsung app (a Tizen web app) lists and opens the TV's apps through tizen.application.
  var tizen = !webos && !!(window.tizen && window.tizen.application);
  var held = []; // a bridge must be kept until it answers

  function luna(uri, params, done) {
    if (!webos) { setTimeout(function () { done(null); }, 0); return; }
    var b = new window.PalmServiceBridge(), over = false;
    held.push(b);
    b.onservicecallback = function (msg) {
      if (over) return;
      over = true;
      held.splice(held.indexOf(b), 1);
      var r = null;
      try { r = JSON.parse(msg); } catch (e) { /* not JSON */ }
      done(r && r.returnValue !== false ? r : null);
    };
    b.call(uri, JSON.stringify(params || {}));
  }

  var self = 'sa.zood.mosquepro.tv';
  // LG's home, launchers and inputs aren't apps to show; the TV's settings get their own tile.
  var HIDDEN = /^(com\.webos\.app\.(home|homeconnect|discovery|launcher|inputpicker|livemenu)|com\.palm\.app\.settings|io\.freemytv.*)$/;
  var COMMON = [
    ['netflix', 'Netflix', '#E50914'], ['youtube.leanback.v4', 'YouTube', '#FF0033'], ['amazon', 'Prime Video', '#1A98FF'],
    ['com.disney.disneyplus-prod', 'Disney+', '#1838C8'], ['shahid', 'Shahid', '#2A9D8F'], ['com.apple.appletv', 'Apple TV', '#B8BCC6'],
    ['spotify-beehive', 'Spotify', '#1DB954'], ['com.webos.app.livetv', 'Live TV', '#3A7BD5'], ['com.webos.app.hdmi1', 'HDMI 1', '#6B7280'],
    ['com.webos.app.hdmi2', 'HDMI 2', '#6B7280'], ['com.webos.app.browser', 'Web Browser', '#3A7BD5']
  ];

  function common(done) {
    var found = [], left = COMMON.length, sent = false;
    function finish() { if (!sent) { sent = true; done(found.filter(Boolean)); } }
    setTimeout(finish, 4000);
    COMMON.forEach(function (c, i) {
      luna('luna://com.webos.applicationManager/getAppLoadStatus', { appId: c[0] }, function (r) {
        if (r && r.exist) found[i] = { id: c[0], title: c[1], icon: '', color: c[2] };
        if (--left === 0) finish();
      });
    });
  }

  // Samsung: apps from the store (a Tizen app id is a 10-letter package id and a name, or a store number), by name.
  var SAMSUNG_OWN = /^(org\.tizen|com\.samsung|samsung|com\.tizen|tizen|org\.volt|com\.sec)\b/i;
  function tizenList(done) {
    try {
      window.tizen.application.getAppsInfo(function (infos) {
        var mine = '';
        try { mine = window.tizen.application.getCurrentApplication().appInfo.id; } catch (e) { /* unknown */ }
        var out = [];
        var seen = {};
        infos.forEach(function (a) {
          if (!a.show || a.id === mine || !a.name || seen[a.name]) return;
          // Samsung's own parts of the TV (settings, sources, the store, helpers) aren't apps to show; nor is
          // anything without an icon.
          if (SAMSUNG_OWN.test(a.id) || !a.iconPath) return;
          seen[a.name] = true;
          out.push({ id: a.id, title: a.name, icon: a.iconPath, color: '' });
        });
        out.sort(function (x, y) { return x.title.localeCompare(y.title); });
        done(out);
      }, function () { done([]); });
    } catch (e) { done([]); }
  }

  window.TVApps = {
    webos: webos,
    /** True where the page can open the TV's apps (the LG and Samsung apps, not a web browser). */
    opens: webos || tizen,
    /** The installed apps: [{id, title, icon, color}], in the TV's own order. */
    list: function (done) {
      if (tizen) { tizenList(done); return; }
      if (!webos) {
        done(COMMON.slice(0, 7).map(function (c) { return { id: c[0], title: c[1], icon: '', color: c[2] }; }));
        return;
      }
      luna('luna://com.webos.applicationManager/listLaunchPoints', {}, function (r) {
        if (!r || !r.launchPoints) { common(done); return; }
        var out = [];
        r.launchPoints.forEach(function (p) {
          if (!p.id || p.id === self || HIDDEN.test(p.id)) return;
          out.push({ id: p.id, title: p.title || p.id, icon: p.largeIcon || p.icon || '', color: p.bgColor || p.iconColor || '' });
        });
        done(out);
      });
    },
    launch: function (id) {
      if (tizen) { try { window.tizen.application.launch(id, function () {}, function () {}); } catch (e) { /* not allowed */ } return; }
      luna('luna://com.webos.applicationManager/launch', { id: id }, function () {});
    },
    settings: function () { luna('luna://com.webos.applicationManager/launch', { id: 'com.palm.app.settings' }, function () {}); },
    /** Back on the home screen: leave the app, to wherever the person came from. */
    back: function () {
      if (window.PalmSystem && window.PalmSystem.platformBack) window.PalmSystem.platformBack();
      else if (webos) window.close();
      else if (window.tizen && window.tizen.application) window.tizen.application.getCurrentApplication().exit();
    },
  };
})();
