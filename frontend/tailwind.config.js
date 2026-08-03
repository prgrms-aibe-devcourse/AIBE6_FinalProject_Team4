/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './app/**/*.{js,jsx,ts,tsx}',
    './components/**/*.{js,jsx,ts,tsx}',
    './lib/**/*.{js,jsx,ts,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        paper: '#FDFBF4',
        ink: '#3E4A3D',
        sub: '#8a9587',
        faint: '#b0b8a6',
        line: '#e6eadd',
        brand: {
          DEFAULT: '#7CB342',
          dark: '#558B2F',
          soft: '#EEF3E4',
          text: '#4b7a1e',
        },
        gold: {
          DEFAULT: '#FFD54F',
          soft: '#FFF6D6',
          text: '#8a6d00',
        },
        danger: {
          DEFAULT: '#c0563a',
          soft: '#fdf1ec',
        },
      },
      fontFamily: {
        sans: ['Pretendard', 'system-ui', '-apple-system', 'sans-serif'],
      },
      boxShadow: {
        card: '0 4px 20px rgba(124,179,66,.08)',
      },
      keyframes: {
        upIn: {
          from: { opacity: '0', transform: 'translateY(10px)' },
          to: { opacity: '1', transform: 'none' },
        },
        pop: {
          '0%': { transform: 'scale(.85)', opacity: '0' },
          '60%': { transform: 'scale(1.03)' },
          '100%': { transform: 'scale(1)', opacity: '1' },
        },
        floaty: {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-8px)' },
        },
        glowPulse: {
          '0%, 100%': { boxShadow: '0 0 0 0 rgba(255,213,79,.6)' },
          '50%': { boxShadow: '0 0 0 9px rgba(255,213,79,0)' },
        },
        confettiFall: {
          '0%': { transform: 'translateY(-16px) rotate(0)', opacity: '1' },
          '100%': { transform: 'translateY(180px) rotate(310deg)', opacity: '0' },
        },
        shimmer: {
          '0%': { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' },
        },
        cardShuffle: {
          '0%, 100%': {
            transform: 'translate3d(0, 0, 0) rotate(0deg) scale(1)',
          },
          '22%': {
            transform: 'translate3d(-72px, -12px, 0) rotate(-9deg) scale(.97)',
          },
          '48%': {
            transform: 'translate3d(68px, -28px, 0) rotate(10deg) scale(1.03)',
          },
          '72%': {
            transform: 'translate3d(-28px, 12px, 0) rotate(-4deg) scale(.99)',
          },
        },
        packTurn: {
          '0%, 38%': {
            transform: 'rotateY(0deg) translateY(0)',
          },
          '48%, 78%': {
            transform: 'rotateY(180deg) translateY(-7px)',
          },
          '88%, 100%': {
            transform: 'rotateY(360deg) translateY(0)',
          },
        },
        packOpening: {
          '0%, 12%, 100%': {
            transform: 'translate3d(0, 0, 0) rotate(0deg)',
          },
          '5%': {
            transform: 'translate3d(-2px, 1px, 0) rotate(-.45deg)',
          },
          '9%': {
            transform: 'translate3d(2px, 0, 0) rotate(.45deg)',
          },
        },
        packTearLine: {
          '0%, 12%': {
            opacity: '0',
            transform: 'scaleX(0)',
          },
          '18%': {
            opacity: '1',
          },
          '52%': {
            opacity: '1',
            transform: 'scaleX(1)',
          },
          '72%, 100%': {
            opacity: '0',
            transform: 'scaleX(1)',
          },
        },
        packTearHandle: {
          '0%, 12%': {
            opacity: '0',
            transform: 'translateX(0) scale(.65)',
          },
          '18%': {
            opacity: '1',
          },
          '54%': {
            opacity: '1',
            transform: 'translateX(min(58vw, 250px)) scale(1)',
          },
          '66%, 100%': {
            opacity: '0',
            transform: 'translateX(min(60vw, 260px)) scale(.7)',
          },
        },
        packAutoTear: {
          '0%, 48%': {
            opacity: '1',
            transform: 'translate3d(0, 0, 20px) rotate(0deg)',
          },
          '68%': {
            opacity: '1',
            transform: 'translate3d(66px, -10px, 48px) rotate(5deg)',
          },
          '100%': {
            opacity: '0',
            transform: 'translate3d(220px, -58px, 90px) rotate(18deg)',
          },
        },
        packBodyOpen: {
          '0%, 48%': {
            transform: 'translateY(0) scale(1)',
          },
          '100%': {
            transform: 'translateY(8px) scale(.985)',
          },
        },
        packCardEmerge: {
          '0%, 45%': {
            opacity: '0',
            transform: 'translate3d(0, 70px, -60px) scale(.8)',
          },
          '66%': {
            opacity: '1',
          },
          '100%': {
            opacity: '1',
            transform: 'translate3d(0, -55px, 80px) scale(.96)',
          },
        },
        packSeamSpark: {
          '0%': {
            opacity: '0',
            transform: 'translate3d(0, 0, 0) scale(.3)',
          },
          '32%': {
            opacity: '1',
          },
          '100%': {
            opacity: '0',
            transform: 'translate3d(14px, -24px, 0) scale(1.2)',
          },
        },
        cardDeal: {
          '0%': {
            opacity: '1',
            transform:
              'translate(-50%, -50%) translate3d(0, -55px, 80px) rotate(0deg) scale(.96)',
          },
          '38%': {
            transform:
              'translate(-50%, -50%) translate3d(var(--cross-x), -26px, 40px) rotate(var(--cross-r)) scale(.94)',
          },
          '66%': {
            transform:
              'translate(-50%, -50%) translate3d(0, 16px, 80px) rotate(0deg) scale(1.04)',
          },
          '100%': {
            opacity: '1',
            transform:
              'translate(-50%, -50%) translate3d(var(--fan-x), var(--fan-y), 0) rotate(var(--fan-r)) scale(1)',
          },
        },
        shuffleSweep: {
          '0%': {
            backgroundPosition: '180% 0',
          },
          '100%': {
            backgroundPosition: '-140% 0',
          },
        },
        shuffleOrbit: {
          from: {
            transform: 'translate(-50%, -50%) rotate(0deg) scale(.92)',
            opacity: '.25',
          },
          to: {
            transform: 'translate(-50%, -50%) rotate(360deg) scale(1.08)',
            opacity: '.7',
          },
        },
        shuffleOrbitReverse: {
          from: {
            transform: 'translate(-50%, -50%) rotate(360deg) scale(1.08)',
            opacity: '.55',
          },
          to: {
            transform: 'translate(-50%, -50%) rotate(0deg) scale(.9)',
            opacity: '.2',
          },
        },
        cardReveal3d: {
          '0%': {
            opacity: '0',
            transform: 'rotateY(-180deg) translateY(28px) scale(.76)',
          },
          '22%': {
            opacity: '1',
          },
          '68%': {
            transform: 'rotateY(8deg) translateY(-5px) scale(1.035)',
          },
          '100%': {
            opacity: '1',
            transform: 'rotateY(0deg) translateY(0) scale(1)',
          },
        },
        revealAura: {
          '0%': {
            opacity: '0',
            transform: 'scale(.45)',
          },
          '45%': {
            opacity: '1',
          },
          '100%': {
            opacity: '.72',
            transform: 'scale(1)',
          },
        },
        rarityParticle: {
          '0%': {
            opacity: '0',
            transform: 'translate3d(0, 0, 0) scale(.25)',
          },
          '22%': {
            opacity: '1',
          },
          '100%': {
            opacity: '0',
            transform:
              'translate3d(var(--particle-x), var(--particle-y), 0) scale(1.25)',
          },
        },
        goldenSweep: {
          '0%': {
            backgroundPosition: '180% 0',
          },
          '45%, 100%': {
            backgroundPosition: '-130% 0',
          },
        },
        premiumBackdrop: {
          '0%': { opacity: '0' },
          '18%, 82%': { opacity: '1' },
          '100%': { opacity: '.72' },
        },
        premiumVignette: {
          '0%': { opacity: '0' },
          '22%': { opacity: '.9' },
          '100%': { opacity: '.34' },
        },
        goldenRadiance: {
          '0%, 34%': {
            opacity: '0',
            transform: 'translate(-50%, -50%) rotate(-12deg) scale(.34)',
          },
          '52%': { opacity: '.86' },
          '100%': {
            opacity: '.24',
            transform: 'translate(-50%, -50%) rotate(16deg) scale(1.18)',
          },
        },
        hyperRadiance: {
          '0%, 22%': {
            opacity: '0',
            transform: 'translate(-50%, -50%) rotate(-20deg) scale(.42)',
          },
          '46%': { opacity: '.82' },
          '100%': {
            opacity: '.2',
            transform: 'translate(-50%, -50%) rotate(22deg) scale(1.14)',
          },
        },
        goldenBurstRing: {
          '0%, 38%': {
            opacity: '0',
            transform: 'translate(-50%, -50%) scale(.28)',
          },
          '53%': { opacity: '1' },
          '100%': {
            opacity: '0',
            transform: 'translate(-50%, -50%) scale(1.55)',
          },
        },
        hyperBurstRing: {
          '0%, 26%': {
            opacity: '0',
            transform: 'translate(-50%, -50%) scale(.34)',
          },
          '44%': { opacity: '1' },
          '100%': {
            opacity: '0',
            transform: 'translate(-50%, -50%) scale(1.45)',
          },
        },
        goldenBloom: {
          '0%, 34%': {
            opacity: '0',
            transform: 'translate(-50%, -50%) scale(.2)',
          },
          '50%': { opacity: '1' },
          '68%': {
            opacity: '.7',
            transform: 'translate(-50%, -50%) scale(1.12)',
          },
          '100%': {
            opacity: '.2',
            transform: 'translate(-50%, -50%) scale(1.32)',
          },
        },
        hyperBloom: {
          '0%, 22%': {
            opacity: '0',
            transform: 'translate(-50%, -50%) scale(.24)',
          },
          '42%': { opacity: '1' },
          '64%': {
            opacity: '.58',
            transform: 'translate(-50%, -50%) scale(1.08)',
          },
          '100%': {
            opacity: '.18',
            transform: 'translate(-50%, -50%) scale(1.26)',
          },
        },
        goldenFlash: {
          '0%, 43%, 58%, 100%': { opacity: '0' },
          '49%': { opacity: '.96' },
          '52%': { opacity: '.72' },
        },
        hyperFlash: {
          '0%, 31%, 49%, 100%': { opacity: '0' },
          '38%': { opacity: '.9' },
          '42%': { opacity: '.48' },
        },
        premiumSpark: {
          '0%': {
            opacity: '0',
            transform: 'translate(-50%, -50%) scale(.2)',
          },
          '12%': { opacity: '1' },
          '72%': { opacity: '.85' },
          '100%': {
            opacity: '0',
            transform:
              'translate(calc(-50% + var(--spark-x)), calc(-50% + var(--spark-y))) scale(.15)',
          },
        },
        premiumFoil: {
          '0%': {
            opacity: '0',
            backgroundPosition: '180% 0',
          },
          '48%': {
            opacity: '0',
            backgroundPosition: '180% 0',
          },
          '66%': {
            opacity: '.78',
          },
          '100%': {
            opacity: '.1',
            backgroundPosition: '-120% 0',
          },
        },
        goldenCelebrationBackdrop: {
          '0%': { opacity: '0' },
          '12%': { opacity: '1' },
          '78%': { opacity: '.92' },
          '100%': { opacity: '.78' },
        },
        goldenCelebrationVignette: {
          '0%, 10%': { opacity: '1' },
          '32%': { opacity: '.38' },
          '100%': { opacity: '.58' },
        },
        goldenFanfareRays: {
          '0%, 18%': {
            opacity: '0',
            transform: 'translate(-50%, -50%) rotate(-16deg) scale(.34)',
          },
          '42%': { opacity: '.82' },
          '72%': {
            opacity: '.48',
            transform: 'translate(-50%, -50%) rotate(8deg) scale(1.02)',
          },
          '100%': {
            opacity: '.26',
            transform: 'translate(-50%, -50%) rotate(20deg) scale(1.12)',
          },
        },
        goldenCelebrationRing: {
          '0%, 28%': {
            opacity: '0',
            transform: 'translate(-50%, -50%) scale(.15)',
          },
          '43%': { opacity: '1' },
          '72%': {
            opacity: '.62',
            transform: 'translate(-50%, -50%) scale(1.06)',
          },
          '100%': {
            opacity: '0',
            transform: 'translate(-50%, -50%) scale(1.46)',
          },
        },
        goldenCelebrationBloom: {
          '0%, 24%': {
            opacity: '0',
            transform: 'translate(-50%, -50%) scale(.12)',
          },
          '39%': {
            opacity: '1',
            transform: 'translate(-50%, -50%) scale(.72)',
          },
          '62%': {
            opacity: '.74',
            transform: 'translate(-50%, -50%) scale(1.15)',
          },
          '100%': {
            opacity: '.18',
            transform: 'translate(-50%, -50%) scale(1.34)',
          },
        },
        goldenLightBeam: {
          '0%, 24%': {
            opacity: '0',
            transform:
              'translateX(-50%) rotate(var(--beam-angle)) scaleY(.05)',
          },
          '42%': { opacity: '.74' },
          '72%': {
            opacity: '.38',
            transform:
              'translateX(-50%) rotate(var(--beam-angle)) scaleY(1)',
          },
          '100%': {
            opacity: '.12',
            transform:
              'translateX(-50%) rotate(var(--beam-angle)) scaleY(1.08)',
          },
        },
        goldenFanfareTitle: {
          '0%, 20%': {
            opacity: '0',
            filter: 'blur(12px) brightness(2)',
            transform: 'translateY(18px) scale(.56)',
          },
          '38%': {
            opacity: '1',
            filter: 'blur(0) brightness(1.8)',
            transform: 'translateY(0) scale(1.12)',
          },
          '52%': {
            filter: 'brightness(1)',
            transform: 'translateY(0) scale(1)',
          },
          '82%': { opacity: '1' },
          '100%': {
            opacity: '.72',
            transform: 'translateY(-4px) scale(.98)',
          },
        },
        goldenFireworkSpark: {
          '0%, 40%': {
            opacity: '0',
            transform: 'translate3d(0, 0, 0) scale(.2)',
          },
          '45%': { opacity: '1' },
          '82%': {
            opacity: '.85',
            transform:
              'translate3d(var(--firework-x), var(--firework-y), 0) scale(1)',
          },
          '100%': {
            opacity: '0',
            transform:
              'translate3d(var(--firework-x), calc(var(--firework-y) + 28px), 0) scale(.25)',
          },
        },
        goldenConfetti: {
          '0%': {
            opacity: '0',
            transform: 'translate3d(0, -4vh, 0) rotate(0deg)',
          },
          '12%': { opacity: '1' },
          '100%': {
            opacity: '0',
            transform:
              'translate3d(var(--confetti-drift), 108vh, 0) rotate(var(--confetti-turn))',
          },
        },
        goldenFanfareFlash: {
          '0%, 25%, 35%, 46%, 100%': { opacity: '0' },
          '29%': { opacity: '.96' },
          '32%': { opacity: '.35' },
          '40%': { opacity: '.62' },
          '43%': { opacity: '.12' },
        },
        goldenCardReveal: {
          '0%': {
            opacity: '1',
            filter: 'brightness(.24) saturate(.55)',
            transform: 'rotateY(-180deg) translateY(26px) scale(.68)',
          },
          '25%': {
            opacity: '1',
            filter: 'brightness(.38) saturate(.7)',
            transform: 'rotateY(-180deg) translateY(14px) scale(.72)',
          },
          '38%': {
            filter: 'brightness(2.4) saturate(1.2)',
            transform: 'rotateY(-180deg) translateY(-2px) scale(.82)',
          },
          '55%': {
            filter: 'brightness(1.5) saturate(1.22)',
            transform: 'rotateY(-86deg) translateY(-12px) scale(1.09)',
          },
          '69%': {
            filter: 'brightness(1.12) saturate(1.08)',
            transform: 'rotateY(8deg) translateY(-8px) scale(1.075)',
          },
          '82%': {
            filter: 'brightness(1.04)',
            transform: 'rotateY(-3deg) translateY(-3px) scale(1.025)',
          },
          '100%': {
            opacity: '1',
            filter: 'brightness(1)',
            transform: 'rotateY(0deg) translateY(0) scale(1)',
          },
        },
        hyperCardReveal: {
          '0%': {
            opacity: '1',
            filter: 'brightness(.55) saturate(.7)',
            transform: 'rotateY(-180deg) translateY(22px) scale(.74)',
          },
          '24%': {
            filter: 'brightness(.7) saturate(.85)',
            transform: 'rotateY(-178deg) translateY(9px) scale(.78)',
          },
          '40%': {
            filter: 'brightness(1.65) saturate(1.35)',
            transform: 'rotateY(-154deg) translateY(-4px) scale(.88)',
          },
          '67%': {
            filter: 'brightness(1.14) saturate(1.14)',
            transform: 'rotateY(-20deg) translateY(-8px) scale(1.055)',
          },
          '82%': {
            transform: 'rotateY(5deg) translateY(-2px) scale(1.02)',
          },
          '100%': {
            opacity: '1',
            filter: 'brightness(1) saturate(1)',
            transform: 'rotateY(0deg) translateY(0) scale(1)',
          },
        },
        stageEnter: {
          '0%': {
            opacity: '.72',
            transform: 'translateY(5px) scale(.985)',
          },
          '100%': {
            opacity: '1',
            transform: 'translateY(0) scale(1)',
          },
        },
      },
      animation: {
        upIn: 'upIn .3s ease',
        pop: 'pop .35s ease',
        floaty: 'floaty 3s ease-in-out infinite',
        glowPulse: 'glowPulse 2s infinite',
        confettiFall: 'confettiFall 1.4s ease-in forwards',
        shimmer: 'shimmer 1.6s ease-in-out infinite',
        cardShuffle: 'cardShuffle 1.45s cubic-bezier(.45,.05,.2,1) infinite',
        packTurn: 'packTurn 5.2s cubic-bezier(.45,.05,.2,1) infinite',
        packOpening: 'packOpening 1.28s ease-out both',
        packTearLine: 'packTearLine 1.28s cubic-bezier(.2,.7,.25,1) both',
        packTearHandle:
          'packTearHandle 1.28s cubic-bezier(.18,.72,.22,1) both',
        packAutoTear: 'packAutoTear 1.28s cubic-bezier(.18,.72,.24,1) both',
        packBodyOpen: 'packBodyOpen 1.28s cubic-bezier(.2,.7,.25,1) both',
        packCardEmerge:
          'packCardEmerge 1.18s cubic-bezier(.16,.75,.25,1) both',
        packSeamSpark: 'packSeamSpark .48s ease-out both',
        cardDeal: 'cardDeal 1.55s cubic-bezier(.2,.72,.22,1) both',
        shuffleSweep: 'shuffleSweep .72s ease-out both',
        shuffleOrbit: 'shuffleOrbit 2.8s linear infinite alternate',
        shuffleOrbitReverse:
          'shuffleOrbitReverse 2.4s linear infinite alternate',
        cardReveal3d:
          'cardReveal3d .92s cubic-bezier(.18,.74,.24,1) both',
        revealAura: 'revealAura 1s cubic-bezier(.2,.7,.25,1) both',
        rarityParticle:
          'rarityParticle 1.25s cubic-bezier(.12,.68,.2,1) both',
        goldenSweep: 'goldenSweep 1.5s ease-out both',
        premiumBackdrop: 'premiumBackdrop 2.45s ease-out both',
        premiumVignette: 'premiumVignette 2.45s ease-out both',
        goldenRadiance:
          'goldenRadiance 2.45s cubic-bezier(.16,.72,.18,1) both',
        hyperRadiance:
          'hyperRadiance 1.85s cubic-bezier(.16,.72,.18,1) both',
        goldenBurstRing:
          'goldenBurstRing 2.15s cubic-bezier(.12,.72,.18,1) both',
        hyperBurstRing:
          'hyperBurstRing 1.6s cubic-bezier(.12,.72,.18,1) both',
        goldenBloom: 'goldenBloom 2.45s cubic-bezier(.16,.72,.2,1) both',
        hyperBloom: 'hyperBloom 1.85s cubic-bezier(.16,.72,.2,1) both',
        goldenFlash: 'goldenFlash 2.45s ease-out both',
        hyperFlash: 'hyperFlash 1.85s ease-out both',
        premiumSpark:
          'premiumSpark 1.25s cubic-bezier(.14,.68,.18,1) both',
        premiumFoil: 'premiumFoil 2.35s ease-out both',
        goldenCelebrationBackdrop: 'goldenCelebrationBackdrop 3.4s ease-out both',
        goldenCelebrationVignette:
          'goldenCelebrationVignette 3.4s ease-out both',
        goldenFanfareRays:
          'goldenFanfareRays 3.4s cubic-bezier(.16,.72,.18,1) both',
        goldenCelebrationRing:
          'goldenCelebrationRing 2.8s cubic-bezier(.12,.72,.18,1) both',
        goldenCelebrationBloom:
          'goldenCelebrationBloom 3.4s cubic-bezier(.16,.72,.2,1) both',
        goldenLightBeam:
          'goldenLightBeam 3.2s cubic-bezier(.16,.72,.2,1) both',
        goldenFanfareTitle:
          'goldenFanfareTitle 3.4s cubic-bezier(.16,.72,.18,1) both',
        goldenFireworkSpark:
          'goldenFireworkSpark 1.5s cubic-bezier(.12,.68,.2,1) both',
        goldenConfetti: 'goldenConfetti 2.2s ease-in both',
        goldenFanfareFlash: 'goldenFanfareFlash 3.4s ease-out both',
        goldenCardReveal:
          'goldenCardReveal 3.4s cubic-bezier(.16,.72,.18,1) both',
        hyperCardReveal:
          'hyperCardReveal 1.85s cubic-bezier(.16,.72,.18,1) both',
        stageEnter: 'stageEnter .24s ease-out both',
      },
    },
  },
  plugins: [],
};
