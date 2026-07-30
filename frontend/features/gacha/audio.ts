import type { GachaRarity } from "@/lib/gacha-api";

type AudioWindow = Window & {
  webkitAudioContext?: typeof AudioContext;
};

let audioContext: AudioContext | null = null;
let noiseBuffer: AudioBuffer | null = null;

function getAudioContext(muted: boolean): AudioContext | null {
  if (muted || typeof window === "undefined") return null;

  const AudioContextConstructor =
    window.AudioContext ?? (window as AudioWindow).webkitAudioContext;
  if (!AudioContextConstructor) return null;

  audioContext ??= new AudioContextConstructor();
  if (audioContext.state === "suspended") {
    void audioContext.resume();
  }
  return audioContext;
}

function getNoiseBuffer(context: AudioContext): AudioBuffer {
  if (noiseBuffer?.sampleRate === context.sampleRate) return noiseBuffer;

  noiseBuffer = context.createBuffer(
    1,
    context.sampleRate * 2,
    context.sampleRate,
  );
  const samples = noiseBuffer.getChannelData(0);
  for (let index = 0; index < samples.length; index += 1) {
    samples[index] = Math.random() * 2 - 1;
  }
  return noiseBuffer;
}

function createMaster(context: AudioContext, volume: number) {
  const gain = context.createGain();
  gain.gain.setValueAtTime(volume, context.currentTime);
  gain.connect(context.destination);
  return gain;
}

function playTone(
  context: AudioContext,
  destination: AudioNode,
  {
    frequency,
    start,
    duration,
    volume,
    type = "sine",
    endFrequency = frequency,
  }: {
    frequency: number;
    start: number;
    duration: number;
    volume: number;
    type?: OscillatorType;
    endFrequency?: number;
  },
) {
  const oscillator = context.createOscillator();
  const gain = context.createGain();
  const startsAt = context.currentTime + start;
  const endsAt = startsAt + duration;

  oscillator.type = type;
  oscillator.frequency.setValueAtTime(frequency, startsAt);
  oscillator.frequency.exponentialRampToValueAtTime(endFrequency, endsAt);
  gain.gain.setValueAtTime(0.0001, startsAt);
  gain.gain.exponentialRampToValueAtTime(volume, startsAt + duration * 0.12);
  gain.gain.exponentialRampToValueAtTime(0.0001, endsAt);
  oscillator.connect(gain);
  gain.connect(destination);
  oscillator.start(startsAt);
  oscillator.stop(endsAt + 0.02);
}

export function playPackTearSound(muted: boolean) {
  const context = getAudioContext(muted);
  if (!context) return;

  const master = createMaster(context, 0.72);
  const noise = context.createBufferSource();
  const filter = context.createBiquadFilter();
  const gain = context.createGain();
  const startsAt = context.currentTime;
  const endsAt = startsAt + 0.7;

  noise.buffer = getNoiseBuffer(context);
  filter.type = "bandpass";
  filter.Q.value = 0.75;
  filter.frequency.setValueAtTime(900, startsAt);
  filter.frequency.exponentialRampToValueAtTime(5_200, startsAt + 0.52);
  gain.gain.setValueAtTime(0.0001, startsAt);
  gain.gain.exponentialRampToValueAtTime(0.42, startsAt + 0.08);
  gain.gain.exponentialRampToValueAtTime(0.0001, endsAt);
  noise.connect(filter);
  filter.connect(gain);
  gain.connect(master);
  noise.start(startsAt);
  noise.stop(endsAt);

  playTone(context, master, {
    frequency: 115,
    endFrequency: 48,
    start: 0,
    duration: 0.32,
    volume: 0.42,
    type: "triangle",
  });
  playTone(context, master, {
    frequency: 1_180,
    endFrequency: 1_760,
    start: 0.38,
    duration: 0.26,
    volume: 0.18,
  });
}

export function playShuffleSound(muted: boolean) {
  const context = getAudioContext(muted);
  if (!context) return;

  const master = createMaster(context, 0.5);
  const buffer = getNoiseBuffer(context);

  for (let index = 0; index < 5; index += 1) {
    const source = context.createBufferSource();
    const filter = context.createBiquadFilter();
    const gain = context.createGain();
    const panner = context.createStereoPanner();
    const startsAt = context.currentTime + index * 0.28;
    const endsAt = startsAt + 0.22;

    source.buffer = buffer;
    filter.type = "bandpass";
    filter.Q.value = 1.3;
    filter.frequency.setValueAtTime(1_100 + index * 140, startsAt);
    filter.frequency.exponentialRampToValueAtTime(3_400, endsAt);
    panner.pan.value = index % 2 === 0 ? -0.65 : 0.65;
    gain.gain.setValueAtTime(0.0001, startsAt);
    gain.gain.exponentialRampToValueAtTime(0.28, startsAt + 0.06);
    gain.gain.exponentialRampToValueAtTime(0.0001, endsAt);
    source.connect(filter);
    filter.connect(gain);
    gain.connect(panner);
    panner.connect(master);
    source.start(startsAt);
    source.stop(endsAt);
  }

  playTone(context, master, {
    frequency: 180,
    endFrequency: 120,
    start: 1.36,
    duration: 0.28,
    volume: 0.2,
    type: "triangle",
  });
}

const REVEAL_CHORDS: Record<GachaRarity, readonly number[]> = {
  COMMON: [392, 523],
  RARE: [440, 587, 740],
  SUPER_RARE: [523, 659, 784],
  HYPER_RARE: [587, 740, 880, 1_175],
  GOLDEN_RARE: [392, 523, 659, 784, 1_047],
};

export function playRarityRevealSound(rarity: GachaRarity, muted: boolean) {
  const context = getAudioContext(muted);
  if (!context) return;

  const golden = rarity === "GOLDEN_RARE";
  const master = createMaster(context, golden ? 0.68 : 0.46);
  const chord = REVEAL_CHORDS[rarity];

  playTone(context, master, {
    frequency: golden ? 72 : 105,
    endFrequency: golden ? 42 : 72,
    start: 0,
    duration: golden ? 0.6 : 0.34,
    volume: golden ? 0.52 : 0.24,
    type: "triangle",
  });

  chord.forEach((frequency, index) => {
    playTone(context, master, {
      frequency,
      endFrequency: frequency * (golden ? 1.5 : 1.18),
      start: golden ? 0.38 + index * 0.12 : index * 0.055,
      duration: golden ? 1.15 : 0.58,
      volume: golden ? 0.16 : 0.11,
      type: index % 2 === 0 ? "sine" : "triangle",
    });
  });

  if (golden) {
    const source = context.createBufferSource();
    const filter = context.createBiquadFilter();
    const gain = context.createGain();
    const startsAt = context.currentTime + 0.4;
    const endsAt = startsAt + 0.95;

    source.buffer = getNoiseBuffer(context);
    filter.type = "highpass";
    filter.frequency.value = 4_200;
    gain.gain.setValueAtTime(0.0001, startsAt);
    gain.gain.exponentialRampToValueAtTime(0.18, startsAt + 0.08);
    gain.gain.exponentialRampToValueAtTime(0.0001, endsAt);
    source.connect(filter);
    filter.connect(gain);
    gain.connect(master);
    source.start(startsAt);
    source.stop(endsAt);
  }
}
