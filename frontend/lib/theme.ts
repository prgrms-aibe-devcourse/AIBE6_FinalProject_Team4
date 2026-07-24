// 키워볼래 design tokens — shared across all pages
export const T = {
  bg: '#FDFBF4',
  ink: '#3E4A3D',
  sub: '#8a9587',
  faint: '#b0b8a6',
  green: '#7CB342',
  greenDark: '#558B2F',
  greenSoft: '#EEF3E4',
  greenText: '#4b7a1e',
  yellow: '#FFD54F',
  yellowSoft: '#FFF6D6',
  yellowText: '#8a6d00',
  danger: '#c0563a',
  dangerSoft: '#fdf1ec',
  line: '#e6eadd',
  card: '#fff',
  shadow: '0 4px 20px rgba(124,179,66,.08)',
  radius: 16,
  font: "Pretendard, system-ui, -apple-system, sans-serif",
};

export const grads = {
  tomato: 'linear-gradient(135deg,#FFCC80,#FF8A65)',
  basil: 'linear-gradient(135deg,#AED581,#7CB342)',
  lettuce: 'linear-gradient(135deg,#C8E6A0,#9CCC65)',
  sprout: 'linear-gradient(135deg,#C5E1A5,#7CB342)',
  sun: 'linear-gradient(135deg,#FFE082,#FFB74D)',
  mint: 'linear-gradient(135deg,#B2DFDB,#4DB6AC)',
  strawberry: 'linear-gradient(135deg,#F8BBD0,#F06292)',
  pepper: 'linear-gradient(135deg,#EF9A9A,#E57373)',
  carrot: 'linear-gradient(135deg,#FFCC80,#FFB74D)',
  potato: 'linear-gradient(135deg,#D7CCC8,#A1887F)',
};

export const won = (n: number | string) => Number(n).toLocaleString('en-US');
