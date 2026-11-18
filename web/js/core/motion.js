/*
 * motion.js — física de mola e gestos, sem dependências.
 *
 * Por que mola e não transition com duração: a mola parte da posição E da velocidade
 * atuais. Soltar o card no meio de um arremesso continua o movimento na mesma velocidade
 * do dedo, e um novo toque pega a animação no meio, sem salto.
 *
 * Modelo (o mesmo da Apple e do Motion): duração perceptual + bounce.
 *   rigidez       = (2π / duração)²
 *   amortecimento = (1 − bounce) · 4π / duração
 */

const reducedQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
export const reducedMotion = () => reducedQuery.matches;

/** Molas nomeadas pelo papel. bounce 0 = UI; bounce > 0 só quando houve energia ou celebração. */
export const SPRING = {
  snappy: { duration: 0.3, bounce: 0 },     // resposta a toque, promover o próximo card
  smooth: { duration: 0.45, bounce: 0 },    // voltar ao lugar sem energia
  gentle: { duration: 0.45, bounce: 0.15 }, // voltar ao lugar depois de um arremesso
  fling: { duration: 0.55, bounce: 0 },     // card saindo da tela
};

function physics({ duration = 0.4, bounce = 0 }) {
  const stiffness = (2 * Math.PI / duration) ** 2;
  const damping = bounce >= 0
    ? ((1 - bounce) * 4 * Math.PI) / duration
    : (4 * Math.PI) / (duration * (1 + bounce));
  return { stiffness, damping };
}

/**
 * Anima várias propriedades numéricas com uma mola.
 * @param {Record<string, number>} from      valores atuais
 * @param {Record<string, number>} to        destino
 * @param {object} options
 *   duration, bounce           — forma da mola (ver SPRING)
 *   velocity                   — velocidade inicial por propriedade (unidades/s)
 *   onUpdate(values)           — chamado a cada frame
 *   until(values)              — encerra antes de assentar (ex.: card já saiu da tela)
 *   rest                       — tolerância de repouso (número ou por propriedade)
 * @returns {{done: Promise<boolean>, stop(): void, value: object}}
 *   done resolve true ao assentar e false se for interrompida por stop().
 */
export function spring(from, to, { duration, bounce, velocity = {}, onUpdate, until, rest = 0.5 } = {}) {
  const { stiffness: k, damping: c } = physics({ duration, bounce });
  const keys = Object.keys(to);
  const x = {};
  const v = {};
  for (const key of keys) { x[key] = from[key] ?? 0; v[key] = velocity[key] ?? 0; }
  const restOf = (key) => (typeof rest === 'number' ? rest : rest[key] ?? 0.5);
  const STEP = 1 / 240; // passo fixo: comportamento igual em 60, 120 ou 144 Hz

  let raf = 0;
  let last = 0;
  let acc = 0;
  let stopped = false;
  let resolveDone;
  const done = new Promise((resolve) => { resolveDone = resolve; });

  function frame(now) {
    if (stopped) return;
    if (!last) last = now;
    acc += Math.min(0.05, (now - last) / 1000);
    last = now;
    while (acc >= STEP) {
      for (const key of keys) {
        const a = -k * (x[key] - to[key]) - c * v[key];
        v[key] += a * STEP;
        x[key] += v[key] * STEP;
      }
      acc -= STEP;
    }
    const settled = keys.every((key) => Math.abs(x[key] - to[key]) < restOf(key) && Math.abs(v[key]) < restOf(key) * 10);
    if (settled) for (const key of keys) { x[key] = to[key]; v[key] = 0; }
    onUpdate?.({ ...x });
    if (settled || until?.(x)) { stopped = true; resolveDone(true); return; }
    raf = requestAnimationFrame(frame);
  }
  raf = requestAnimationFrame(frame);

  return {
    done,
    get value() { return { ...x }; },
    stop() {
      if (stopped) return;
      stopped = true;
      cancelAnimationFrame(raf);
      resolveDone(false);
    },
  };
}

/** Velocidade do gesto a partir das amostras dos últimos 100 ms (uma diferença só é ruidosa demais). */
export class VelocityTracker {
  constructor() { this.samples = []; }
  add(x, y, t = performance.now()) {
    this.samples.push({ x, y, t });
    while (this.samples.length > 2 && this.samples[0].t < t - 100) this.samples.shift();
  }
  velocity(now = performance.now()) {
    const s = this.samples;
    if (s.length < 2) return { x: 0, y: 0 };
    const first = s[0];
    const last = s[s.length - 1];
    if (now - last.t > 100) return { x: 0, y: 0 }; // parou antes de soltar
    const dt = (last.t - first.t) / 1000;
    if (dt <= 0) return { x: 0, y: 0 };
    return { x: (last.x - first.x) / dt, y: (last.y - first.y) / dt };
  }
}

/** Resistência progressiva além de um limite (a fórmula do UIScrollView). */
export function rubberBand(offset, dimension, constant = 0.55) {
  return Math.sign(offset) * (1 - 1 / ((Math.abs(offset) * constant) / dimension + 1)) * dimension;
}

/** Projeção de momentum: onde o gesto pararia se continuasse com essa velocidade. */
export function project(velocity, rate = 0.998) {
  return ((velocity / 1000) * rate) / (1 - rate);
}

export const clamp = (value, min, max) => Math.min(max, Math.max(min, value));

/** Háptica curta onde existe (Android). No iOS o navegador ignora; o visual carrega o feedback. */
export function haptic(pattern = 8) {
  try { navigator.vibrate?.(pattern); } catch { /* sem suporte */ }
}
