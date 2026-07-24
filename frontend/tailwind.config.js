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
      },
      animation: {
        upIn: 'upIn .3s ease',
        pop: 'pop .35s ease',
        floaty: 'floaty 3s ease-in-out infinite',
        glowPulse: 'glowPulse 2s infinite',
        confettiFall: 'confettiFall 1.4s ease-in forwards',
        shimmer: 'shimmer 1.6s ease-in-out infinite',
      },
    },
  },
  plugins: [],
};
