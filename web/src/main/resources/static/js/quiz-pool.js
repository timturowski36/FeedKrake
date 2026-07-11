// Quiz-Fragenpool + datums-geseedete deterministische Auswahl (NOO-144).
// Hash+LCG-Algorithmus 1:1 aus docs/frontend/Kalender.dc.html portiert, damit
// dieselbe Woche/derselbe Tag beim erneuten Laden immer dieselben 3 Fragen zeigt.

export const QUIZ_POOL = {
  "Geschichte": [
    { q: "In welchem Jahr fiel die Berliner Mauer?", a: ["1987", "1989", "1991", "1993"], c: 1 },
    { q: "Wer war der erste Bundeskanzler der BRD?", a: ["Willy Brandt", "Konrad Adenauer", "Ludwig Erhard", "Helmut Schmidt"], c: 1 },
    { q: "Welches Reich wurde 1806 aufgelöst?", a: ["Römisches Reich", "Heiliges Römisches Reich", "Byzantinisches Reich", "Osmanisches Reich"], c: 1 },
  ],
  "Geografie": [
    { q: "Was ist die Hauptstadt von Australien?", a: ["Sydney", "Melbourne", "Canberra", "Perth"], c: 2 },
    { q: "Welcher Fluss ist der längste der Welt?", a: ["Amazonas", "Nil", "Jangtse", "Mississippi"], c: 1 },
    { q: "Welches Land hat die meisten Zeitzonen?", a: ["USA", "Russland", "Frankreich", "China"], c: 2 },
  ],
  "Naturwissenschaft": [
    { q: "Welches chemische Element hat das Symbol 'Fe'?", a: ["Fluor", "Eisen", "Francium", "Fermium"], c: 1 },
    { q: "Wie viele Knochen hat ein erwachsener Mensch?", a: ["186", "206", "226", "246"], c: 1 },
    { q: "Was misst man in Pascal?", a: ["Temperatur", "Druck", "Energie", "Kraft"], c: 1 },
  ],
  "Sport": [
    { q: "Wie viele Spieler stehen pro Mannschaft beim Handball auf dem Feld?", a: ["5", "6", "7", "8"], c: 2 },
    { q: "In welcher Sportart gibt es den 'Grand Slam'?", a: ["Golf", "Tennis", "Boxen", "Beides (Tennis & Golf)"], c: 3 },
    { q: "Wie lang ist ein Marathon?", a: ["40,2 km", "41,2 km", "42,195 km", "43,2 km"], c: 2 },
  ],
  "Kunst & Kultur": [
    { q: "Wer malte die 'Mona Lisa'?", a: ["Michelangelo", "Raffael", "Leonardo da Vinci", "Donatello"], c: 2 },
    { q: "Welcher Komponist war taub, als er seine 9. Sinfonie schrieb?", a: ["Mozart", "Bach", "Beethoven", "Brahms"], c: 2 },
    { q: "In welcher Stadt steht das Kolosseum?", a: ["Athen", "Rom", "Neapel", "Mailand"], c: 1 },
  ],
  "Technik": [
    { q: "Wofür steht die Abkürzung 'HTTP'?", a: ["High Transfer Text Protocol", "HyperText Transfer Protocol", "Home Tool Transfer Protocol", "HyperText Transmission Process"], c: 1 },
    { q: "Wer gilt als Erfinder des World Wide Web?", a: ["Bill Gates", "Steve Jobs", "Tim Berners-Lee", "Alan Turing"], c: 2 },
    { q: "Was bedeutet 'CPU'?", a: ["Central Processing Unit", "Computer Personal Unit", "Central Program Utility", "Core Processing Unit"], c: 0 },
  ],
};

function hash(s) {
  let h = 7;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
  return h;
}

function lcg(seed) {
  let s = seed >>> 0;
  return () => {
    s = (s * 1103515245 + 12345) >>> 0;
    return s / 4294967296;
  };
}

/** 3 deterministische, eindeutige Fragen für ein Datum aus den gewählten Themen (oder allen, falls leer). */
export function questionsForDate(dateKey, topics) {
  const activeTopics = topics && topics.length ? topics : Object.keys(QUIZ_POOL);
  const rand = lcg(hash("qz" + dateKey));
  const picked = [];
  const seen = new Set();
  let guard = 0;
  while (picked.length < 3 && guard < 200) {
    guard++;
    const topic = activeTopics[Math.floor(rand() * activeTopics.length)];
    const pool = QUIZ_POOL[topic];
    if (!pool || !pool.length) continue;
    const q = pool[Math.floor(rand() * pool.length)];
    const key = topic + "|" + q.q;
    if (seen.has(key)) continue;
    seen.add(key);
    picked.push({ topic, ...q });
  }
  return picked;
}
