import { existsSync, readFileSync, readdirSync } from "node:fs";
import path from "node:path";

const EXPECTED_WIDTH = 1122;
const EXPECTED_HEIGHT = 1402;
// 43개 카드 일러스트는 S3로 옮겨졌고 더 이상 리포에 들어있지 않다(백엔드 TradingCard.imageKey 참고).
// 남은 manifest 항목은 공유 가챠 UI 자산(팩/카드 뒷면)뿐이라 로컬 CARD_ILLUSTRATION은 0개가 정상이다.
const EXPECTED_CARD_COUNT = 0;
const CARD_PATH_PATTERN =
  /^\/cards\/([1-9]\d*)\/([0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\.(png|svg)$/;
const PNG_SIGNATURE = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
const publicDirectory = path.resolve(process.cwd(), "public");
const cardsDirectory = path.join(publicDirectory, "cards");
const manifestPath = path.join(cardsDirectory, "manifest.json");

const errors = [];
const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
const ids = new Set();
const uuids = new Set();
const manifestPaths = new Set();
let checkedCardCount = 0;

for (const asset of manifest.assets) {
  const match = CARD_PATH_PATTERN.exec(asset.path);
  if (!match) {
    errors.push(`${asset.code}: invalid asset path ${asset.path}`);
    continue;
  }

  const pathId = Number(match[1]);
  const uuid = match[2];
  const extension = match[3];

  if (pathId !== asset.id) {
    errors.push(`${asset.code}: path ID ${pathId} does not match ID ${asset.id}`);
  }
  if (ids.has(asset.id)) {
    errors.push(`${asset.code}: duplicate ID ${asset.id}`);
  }
  if (uuids.has(uuid)) {
    errors.push(`${asset.code}: duplicate UUID ${uuid}`);
  }
  ids.add(asset.id);
  uuids.add(uuid);
  manifestPaths.add(asset.path);

  const filePath = path.join(publicDirectory, asset.path);
  if (!existsSync(filePath)) {
    errors.push(`${asset.code}: missing file ${asset.path}`);
    continue;
  }

  if (asset.type !== "CARD_ILLUSTRATION") {
    continue;
  }

  checkedCardCount += 1;
  if (extension !== "png") {
    errors.push(`${asset.code}: card illustration must retain PNG format`);
    continue;
  }

  const image = readFileSync(filePath);
  if (image.length < 24 || !image.subarray(0, 8).equals(PNG_SIGNATURE)) {
    errors.push(`${asset.code}: invalid PNG file`);
    continue;
  }

  const width = image.readUInt32BE(16);
  const height = image.readUInt32BE(20);
  if (width !== EXPECTED_WIDTH || height !== EXPECTED_HEIGHT) {
    errors.push(
      `${asset.code}: ${width}x${height} (expected ${EXPECTED_WIDTH}x${EXPECTED_HEIGHT})`,
    );
  }
}

const actualAssetPaths = [];
for (const directory of readdirSync(cardsDirectory, { withFileTypes: true })) {
  if (!directory.isDirectory()) {
    continue;
  }
  const directoryPath = path.join(cardsDirectory, directory.name);
  for (const file of readdirSync(directoryPath, { withFileTypes: true })) {
    if (file.isFile()) {
      actualAssetPaths.push(`/cards/${directory.name}/${file.name}`);
    }
  }
}

for (const assetPath of actualAssetPaths) {
  if (!manifestPaths.has(assetPath)) {
    errors.push(`unregistered asset file ${assetPath}`);
  }
}

if (actualAssetPaths.length !== manifestPaths.size) {
  errors.push(
    `asset count mismatch: ${actualAssetPaths.length} files, ${manifestPaths.size} manifest entries`,
  );
}

if (checkedCardCount !== EXPECTED_CARD_COUNT) {
  errors.push(
    `expected ${EXPECTED_CARD_COUNT} card illustrations, found ${checkedCardCount}`,
  );
}

if (errors.length > 0) {
  console.error("Card asset validation failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log(
  `Card asset validation passed: ${checkedCardCount} card illustrations, ${manifest.assets.length} unique IDs and UUIDs`,
);
