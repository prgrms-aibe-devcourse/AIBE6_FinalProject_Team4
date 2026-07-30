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
        goldenFlash: {
          '0%, 56%, 100%': {
            opacity: '0',
          },
          '62%': {
            opacity: '.72',
          },
        },
        goldenBackdrop: {
          '0%': {
            opacity: '0',
          },
          '28%, 100%': {
            opacity: '1',
          },
        },
        goldenVeil: {
          '0%, 20%': {
            opacity: '.94',
          },
          '38%': {
            opacity: '.7',
          },
          '58%, 100%': {
            opacity: '0',
          },
        },
        goldenOmenLine: {
          '0%': {
            opacity: '0',
            transform: 'scaleX(0)',
          },
          '18%': {
            opacity: '1',
            transform: 'scaleX(.12)',
          },
          '42%': {
            opacity: '.9',
            transform: 'scaleX(.78)',
          },
          '58%, 100%': {
            opacity: '0',
            transform: 'scaleX(1)',
          },
        },
        goldenBeamLeft: {
          '0%, 34%': {
            opacity: '0',
            transform: 'translate(-50%, -50%) skewX(-12deg) scaleX(.3)',
          },
          '64%': {
            opacity: '.8',
            transform: 'translate(-82%, -50%) skewX(-12deg) scaleX(1)',
          },
          '100%': {
            opacity: '.16',
            transform: 'translate(-105%, -50%) skewX(-12deg) scaleX(1.2)',
          },
        },
        goldenBeamRight: {
          '0%, 34%': {
            opacity: '0',
            transform: 'translate(-50%, -50%) skewX(12deg) scaleX(.3)',
          },
          '64%': {
            opacity: '.72',
            transform: 'translate(-18%, -50%) skewX(12deg) scaleX(1)',
          },
          '100%': {
            opacity: '.14',
            transform: 'translate(5%, -50%) skewX(12deg) scaleX(1.2)',
          },
        },
        goldenHalo: {
          '0%, 38%': {
            opacity: '0',
            transform: 'translate(-50%, -50%) scale(.88)',
          },
          '68%': {
            opacity: '.75',
          },
          '100%': {
            opacity: '.32',
            transform: 'translate(-50%, -50%) scale(1.035)',
          },
        },
        goldenFoil: {
          '0%': {
            opacity: '0',
            backgroundPosition: '180% 0',
          },
          '62%': {
            opacity: '0',
            backgroundPosition: '180% 0',
          },
          '78%': {
            opacity: '.65',
          },
          '100%': {
            opacity: '.16',
            backgroundPosition: '-120% 0',
          },
        },
        goldenCardReveal: {
          '0%': {
            opacity: '1',
            transform: 'rotateY(-180deg) translateY(12px) scale(.82)',
          },
          '28%': {
            opacity: '1',
            transform: 'rotateY(-180deg) translateY(8px) scale(.82)',
          },
          '44%': {
            transform: 'rotateY(-176deg) translateY(0) scale(.86)',
          },
          '70%': {
            transform: 'rotateY(-50deg) translateY(-2px) scale(1.015)',
          },
          '82%': {
            transform: 'rotateY(3deg) translateY(0) scale(1.02)',
          },
          '100%': {
            opacity: '1',
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
        goldenFlash: 'goldenFlash 2.05s ease-out both',
        goldenBackdrop: 'goldenBackdrop 2.1s ease-out both',
        goldenVeil: 'goldenVeil 2.05s ease-out both',
        goldenOmenLine:
          'goldenOmenLine 2.05s cubic-bezier(.18,.7,.2,1) both',
        goldenBeamLeft:
          'goldenBeamLeft 2.1s cubic-bezier(.18,.7,.2,1) both',
        goldenBeamRight:
          'goldenBeamRight 2.1s cubic-bezier(.18,.7,.2,1) both',
        goldenHalo: 'goldenHalo 2.1s cubic-bezier(.18,.7,.2,1) both',
        goldenFoil: 'goldenFoil 2.3s ease-out both',
        goldenCardReveal:
          'goldenCardReveal 2.05s cubic-bezier(.16,.7,.18,1) both',
        stageEnter: 'stageEnter .24s ease-out both',
      },
    },
  },
  plugins: [],
};
