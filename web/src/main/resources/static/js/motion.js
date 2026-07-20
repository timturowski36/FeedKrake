// Motion-Primitive nach Apple "Designing Fluid Interfaces" (WWDC 2018):
// Federn statt fester Dauern, Momentum-Projektion, Rubber-Banding.
// Bewusst ohne Abhängigkeit — die App liefert nur statische ES-Module aus.

/**
 * Apple-Parametrisierung statt Masse/Steifigkeit/Dämpfung:
 *   damping  1.0 = kritisch gedämpft, kein Überschwingen; < 1.0 schwingt über
 *   response      Zeit in Sekunden, bis der Wert am Ziel ist (keine "Dauer" —
 *                 die Einschwingzeit ergibt sich aus den Parametern)
 * Überschwingen nur, wo die Geste selbst Schwung getragen hat.
 */
export const SPRING = {
  calm: { damping: 1.0, response: 0.30 },  // Standard-UI: Tipp-Öffnung, Einrasten ohne Schwung
  move: { damping: 1.0, response: 0.40 },  // Verschieben/Repositionieren
  sheet: { damping: 0.8, response: 0.30 }, // Drawer/Sheet nach einer Wurfgeste
  snap: { damping: 0.8, response: 0.35 },  // Seitenwechsel nach einem Flick
};

export function prefersReducedMotion() {
  return matchMedia("(prefers-reduced-motion: reduce)").matches;
}

/**
 * Skalar-Feder mit analytischer Lösung (kein Integrationsdrift).
 *
 * Jedes to() startet beim *aktuellen* Wert und der *aktuellen* Geschwindigkeit.
 * Genau das braucht Unterbrechbarkeit: eine laufende Animation lässt sich mitten
 * im Flug greifen und umlenken — ohne sichtbaren Sprung und ohne die
 * Geschwindigkeits-Bruchkante, die entsteht, wenn man eine Animation hart durch
 * eine andere ersetzt.
 */
export class Spring {
  constructor(value, onUpdate, preset = SPRING.calm) {
    this.value = value;
    this.velocity = 0;
    this.target = value;
    this.preset = preset;
    this.onUpdate = onUpdate;
    this._raf = 0;
    this._onRest = null;
  }

  /** Wert ohne Animation setzen — für 1:1-Tracking während einer Geste. */
  set(value, velocity = 0) {
    this._cancel();
    this.value = value;
    this.velocity = velocity;
    this.target = value;
    this.onUpdate(value);
  }

  /** Laufende Animation anhalten, Wert und Geschwindigkeit bleiben erhalten. */
  stop() { this._cancel(); }

  /**
   * Neues Ziel anfahren. velocity überschreibt die mitgeführte Geschwindigkeit
   * (Handoff aus einer Geste, §5); ohne Angabe läuft die aktuelle weiter, damit
   * eine Umkehr keine Bruchkante bekommt.
   */
  to(target, { velocity, preset, onRest } = {}) {
    if (velocity != null) this.velocity = velocity;
    if (preset) this.preset = preset;
    this.target = target;
    this._onRest = onRest ?? null;

    // Reduzierte Bewegung: kein vestibulärer Weg, direkt am Ziel landen.
    // Das Feedback bleibt (Opazität/Farbe), nur die Strecke entfällt.
    if (prefersReducedMotion()) {
      this._cancel();
      this.value = target;
      this.velocity = 0;
      this.onUpdate(target);
      this._onRest?.();
      return;
    }
    this._run();
  }

  _cancel() {
    if (this._raf) cancelAnimationFrame(this._raf);
    this._raf = 0;
  }

  _run() {
    this._cancel();
    const { damping: z, response } = this.preset;
    const w0 = (2 * Math.PI) / response;
    const x0 = this.value - this.target;
    const v0 = this.velocity;
    const t0 = performance.now();

    // Geschlossene Lösung der gedämpften Schwingung, je nach Dämpfungsgrad.
    let solve;
    if (z < 1) {
      const wd = w0 * Math.sqrt(1 - z * z);
      const A = x0;
      const B = (v0 + z * w0 * x0) / wd;
      solve = t => {
        const e = Math.exp(-z * w0 * t), c = Math.cos(wd * t), s = Math.sin(wd * t);
        return [e * (A * c + B * s), e * ((B * wd - z * w0 * A) * c - (A * wd + z * w0 * B) * s)];
      };
    } else {
      const C = v0 + w0 * x0;
      solve = t => {
        const e = Math.exp(-w0 * t);
        return [e * (x0 + C * t), e * (C * (1 - w0 * t) - w0 * x0)];
      };
    }

    const tick = now => {
      const [x, v] = solve((now - t0) / 1000);
      this.value = this.target + x;
      this.velocity = v;
      // Ruhezustand: so nah am Ziel und so langsam, dass der nächste Frame
      // ohnehin unter der Wahrnehmungsschwelle bliebe.
      if (Math.abs(x) < 0.05 && Math.abs(v) < 0.05) {
        this.value = this.target;
        this.velocity = 0;
        this._raf = 0;
        this.onUpdate(this.value);
        this._onRest?.();
        return;
      }
      this.onUpdate(this.value);
      this._raf = requestAnimationFrame(tick);
    };
    this._raf = requestAnimationFrame(tick);
  }
}

/**
 * Momentum-Projektion: wohin trägt der Schwung den Wert, wenn man jetzt loslässt?
 * Exponentieller Abfall wie bei der Scroll-Verzögerung — nicht die
 * Lehrbuchformel v²/2a. Damit wirft ein kurzer Flick weit, statt zum nächsten
 * Rastpunkt am Loslasspunkt zu springen.
 */
export function project(velocity, decelerationRate = 0.998) {
  return (velocity / 1000) * decelerationRate / (1 - decelerationRate);
}

/**
 * Progressiver Widerstand jenseits einer Grenze: je weiter darüber hinaus, desto
 * weniger folgt das Element. Ein harter Stopp liest sich als "eingefroren", ein
 * weicher Rand als "reagiert, aber hier ist Schluss".
 */
export function rubberband(overshoot, dimension, constant = 0.55) {
  return (overshoot * dimension * constant) / (dimension + constant * Math.abs(overshoot));
}

/**
 * Geschwindigkeits-Historie über ein kurzes Fenster. Nur die letzten Millisekunden
 * zählen — sonst dämpft ein langsamer Gestenanfang den Flick am Ende weg, und ein
 * Finger, der vor dem Loslassen stehen bleibt, würde trotzdem noch werfen.
 */
export function createVelocityTracker(windowMs = 80) {
  let samples = [];
  return {
    reset() { samples = []; },
    add(value, time = performance.now()) {
      samples.push({ value, time });
      while (samples.length > 2 && samples[0].time < time - windowMs * 3) samples.shift();
    },
    /** px/s über das Zeitfenster; 0, wenn die Geste zum Stillstand gekommen ist. */
    get(time = performance.now()) {
      const recent = samples.filter(s => time - s.time <= windowMs);
      const pts = recent.length >= 2 ? recent : samples.slice(-2);
      if (pts.length < 2) return 0;
      const first = pts[0], last = pts[pts.length - 1];
      const dt = (last.time - first.time) / 1000;
      return dt > 0 ? (last.value - first.value) / dt : 0;
    },
  };
}

/**
 * Kurzer Haptik-Impuls an Commit-Momenten (Einrasten, Verwerfen). Muss im selben
 * Frame wie das Sichtbare feuern, sonst zerfällt die Illusion. Bewusst nur als
 * Zugabe: iOS Safari kennt die Vibration API nicht, das Visuelle trägt allein.
 */
export function haptic(ms = 8) {
  try { navigator.vibrate?.(ms); } catch { /* Plattform ohne Haptik */ }
}
