import { execFileSync } from 'node:child_process';
import { mkdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const publicDirectory = resolve('public');
const manifest = JSON.parse(
  readFileSync(resolve(publicDirectory, 'cards/manifest.json'), 'utf8'),
);
const frame = manifest.assets.find(
  (asset) => asset.type === 'GACHA_FRAME' && asset.code === 'common_frame_png',
);
const commonCards = manifest.assets
  .filter(
    (asset) =>
      asset.type === 'CARD_ILLUSTRATION' && asset.rarity === 'COMMON',
  )
  .sort((left, right) => left.id - right.id);

if (!frame) {
  throw new Error('common_frame_png is missing from the card asset manifest');
}

const framePath = resolve(publicDirectory, `.${frame.path}`);

const outputArgumentIndex = process.argv.indexOf('--output-dir');
const outputDirectory =
  outputArgumentIndex >= 0 && process.argv[outputArgumentIndex + 1]
    ? resolve(process.argv[outputArgumentIndex + 1])
    : null;

if (outputDirectory) {
  mkdirSync(outputDirectory, { recursive: true });
}

for (const card of commonCards) {
  const inputPath = resolve(publicDirectory, `.${card.path}`);
  const outputPath = outputDirectory
    ? resolve(outputDirectory, card.path.split('/').at(-1))
    : inputPath;
  const temporaryOutputPath = `${outputPath}.normalizing.png`;

  const filter = [
    '[0:v]split=2[scene][icon]',
    '[scene]crop=1012:1312:55:45,scale=1046:1356:flags=lanczos,crop=1046:1346:0:5,pad=1122:1402:38:28:color=0xEEDDB8[base]',
    '[icon]crop=80:80:60:55,scale=104:104:flags=lanczos[icon104]',
    '[base][icon104]overlay=44:44[with_icon]',
    '[with_icon][1:v]overlay=0:0:format=auto[final]',
  ].join(';');

  execFileSync(
    'ffmpeg',
    [
      '-hide_banner',
      '-loglevel',
      'error',
      '-y',
      '-i',
      inputPath,
      '-i',
      framePath,
      '-filter_complex',
      filter,
      '-map',
      '[final]',
      '-frames:v',
      '1',
      '-c:v',
      'png',
      temporaryOutputPath,
    ],
    { stdio: 'inherit' },
  );

  execFileSync('mv', [temporaryOutputPath, outputPath]);
  console.log(`normalized ${card.code}: ${card.path}`);
}
