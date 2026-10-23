// The mosque's own timetable on the TV screen: the wait from each Adhan to its iqama, the Jumu'ah, the calculation
// method, the Asr rule and minutes added to each time. Kept on the TV (localStorage), set from the settings row.
// The iqama defaults are the common ones in Saudi mosques and match the apps' (NMCore IqamaSettings).
//
// Plain functions only, so tools/check_tv.mjs can test them in Node and tools/webos/build.py can join this file to
// the others as one script (names here must not clash with tv.js or prayer.js).
export const IQAMA_DEFAULTS = { fajr: 25, dhuhr: 20, asr: 20, maghrib: 10, isha: 20 };
export const IQAMA_CHOICES = [0, 5, 10, 15, 20, 25, 30, 35, 40, 45];
// Friday: minutes from the Adhan to the prayer (the khutbah). 0 shows the Adhan screen briefly and no countdown.
export const JUMUAH_CHOICES = [0, 15, 20, 25, 30, 35, 40, 45, 60];
export const JUMUAH_BRIEF = 5;
export const OFFSET_CHOICES = [-10, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 10];
export const MOSQUE_KEY = 'mp-tv-mosque';

// What is saved, with anything missing or out of range back to its default.
export function mosqueSettings(raw) {
  let s = {};
  try { s = JSON.parse(raw || '{}') || {}; } catch (e) { s = {}; }
  const iq = {};
  Object.keys(IQAMA_DEFAULTS).forEach((k) => {
    const v = s.iqama && s.iqama[k];
    iq[k] = IQAMA_CHOICES.indexOf(v) >= 0 ? v : IQAMA_DEFAULTS[k];
  });
  const off = {};
  ['fajr', 'sunrise', 'dhuhr', 'asr', 'maghrib', 'isha'].forEach((k) => {
    const v = s.offsets && s.offsets[k];
    off[k] = OFFSET_CHOICES.indexOf(v) >= 0 ? v : 0;
  });
  return {
    iqama: iq,
    jumuah: JUMUAH_CHOICES.indexOf(s.jumuah) >= 0 ? s.jumuah : 0,
    method: typeof s.method === 'string' ? s.method : 'auto',
    asr: s.asr === 'hanafi' ? 'hanafi' : 'standard',
    offsets: off,
  };
}

// Minutes from this prayer's Adhan to its iqama; on Friday Dhuhr is the Jumu'ah.
export function iqamaMinutes(cfg, k, friday) {
  if (k === 'dhuhr' && friday) return cfg.jumuah;
  return cfg.iqama[k] || 0;
}

// The Adhan screen: from a prayer's time until its iqama (or briefly, with no iqama). null at any other time.
// plan.today is [[key, Date], ...]; isFriday(date) says whether a moment is on Friday where the TV is.
export function adhanWindow(plan, now, cfg, isFriday) {
  for (let i = 0; i < plan.today.length; i++) {
    const k = plan.today[i][0], v = plan.today[i][1];
    if (k === 'sunrise') continue;
    const wait = iqamaMinutes(cfg, k, isFriday(v));
    const end = +v + (wait || JUMUAH_BRIEF) * 60e3;
    if (+now >= +v && +now < end) return { k, at: v, wait, iqama: wait ? new Date(+v + wait * 60e3) : null };
  }
  return null;
}
