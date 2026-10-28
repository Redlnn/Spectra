import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import sharp from "sharp";

interface IconPath {
  data: string;
  fill: string | undefined;
  stroke: string | undefined;
  strokeWidth: string | undefined;
}

const project = fileURLToPath(new URL("../", import.meta.url));
const source = fs.readFileSync(
  path.join(project, "design/app-icon.svg"),
  "utf8",
);
const version = fs
  .readFileSync(path.join(project, "app/build.gradle.kts"), "utf8")
  .match(/versionName\s*=\s*"([\d.]+)"/)?.[1];
if (!version) throw new Error("Cannot read application version");

const output = path.join(project, "artifacts/icons", version);
const res = path.join(project, "app/src/main/res");
const launcherScale = 0.8;
const background = source.match(/<rect[^>]+fill="([^"]+)"/)?.[1];
const pathElements = Array.from(source.matchAll(/<path\b([^>]+)\/>/g));
const paths: IconPath[] = pathElements.map((match) => {
  const attributes: Record<string, string> = Object.fromEntries(
    Array.from(
      (match[1] ?? "").matchAll(/([\w-]+)="([^"]*)"/g),
      (attribute) => [attribute[1], attribute[2]],
    ),
  );
  const data = attributes.d;
  if (!data) throw new Error("Icon path is missing its path data");
  if (attributes.stroke && !attributes["stroke-width"]) {
    throw new Error("Stroked icon path is missing its stroke width");
  }
  return {
    data,
    fill: attributes.fill,
    stroke: attributes.stroke,
    strokeWidth: attributes["stroke-width"],
  };
});
if (!background || !paths.length || !source.includes('viewBox="0 0 108 108"')) {
  throw new Error("Expected a 108dp icon with a background and vector paths");
}

function vector(monochrome: boolean, scale = 1): string {
  const elements = paths.map((iconPath) => {
    const fill =
      iconPath.fill && iconPath.fill !== "none"
        ? monochrome
          ? "#FFFFFF"
          : iconPath.fill
        : "#00000000";
    const stroke = iconPath.stroke
      ? ` android:strokeColor="${monochrome ? "#FFFFFF" : iconPath.stroke}" android:strokeWidth="${iconPath.strokeWidth}" android:strokeLineCap="round" android:strokeLineJoin="round"`
      : "";
    return `  <path android:pathData="${iconPath.data}" android:fillColor="${fill}"${stroke}/>`;
  });
  const content = elements.join("\n");
  const scaled =
    scale === 1
      ? content
      : `  <group android:pivotX="54" android:pivotY="54" android:scaleX="${scale}" android:scaleY="${scale}">\n${content}\n  </group>`;
  return `<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n${scaled}\n</vector>\n`;
}

async function main(): Promise<void> {
  fs.mkdirSync(output, { recursive: true });
  fs.writeFileSync(path.join(output, "B-selected.svg"), source);
  await sharp(Buffer.from(source))
    .png()
    .toFile(path.join(output, "B-selected.png"));
  fs.writeFileSync(path.join(res, "drawable/spectra_icon.xml"), vector(false));
  fs.writeFileSync(
    path.join(res, "drawable/spectra_icon_monochrome.xml"),
    vector(true),
  );
  fs.writeFileSync(
    path.join(res, "drawable/spectra_launcher_foreground.xml"),
    vector(false, launcherScale),
  );
  fs.writeFileSync(
    path.join(res, "drawable/spectra_launcher_monochrome.xml"),
    vector(true, launcherScale),
  );

  const colors = path.join(res, "values/colors.xml");
  fs.writeFileSync(
    colors,
    fs
      .readFileSync(colors, "utf8")
      .replace(
        /(<color name="spectra_icon_background">)[^<]+/,
        `$1${background}`,
      ),
  );
  const adaptive =
    '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/spectra_icon_background"/><foreground android:drawable="@drawable/spectra_launcher_foreground"/><monochrome android:drawable="@drawable/spectra_launcher_monochrome"/></adaptive-icon>\n';
  for (const name of ["ic_launcher.xml", "ic_launcher_round.xml"]) {
    fs.writeFileSync(path.join(res, "mipmap-anydpi-v26", name), adaptive);
  }

  const pathMarkup = pathElements.map((match) => match[0]).join("");
  const launcherMarkup = `<g transform="translate(54 54) scale(${launcherScale}) translate(-54 -54)">${pathMarkup}</g>`;
  for (const [name, mask] of Object.entries({
    circle: '<circle cx="54" cy="54" r="36"/>',
    rounded: '<rect x="18" y="18" width="72" height="72" rx="22"/>',
  })) {
    const preview = `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="18 18 72 72"><defs><clipPath id="mask">${mask}</clipPath></defs><g clip-path="url(#mask)"><rect width="108" height="108" fill="${background}"/>${launcherMarkup}</g></svg>`;
    await sharp(Buffer.from(preview))
      .png()
      .toFile(path.join(output, `launcher-${name}.png`));
  }
  const round = `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108"><defs><clipPath id="round"><circle cx="54" cy="54" r="54"/></clipPath></defs><g clip-path="url(#round)"><rect width="108" height="108" fill="${background}"/>${pathMarkup}</g></svg>`;
  const densities = {
    mdpi: 48,
    hdpi: 72,
    xhdpi: 96,
    xxhdpi: 144,
    xxxhdpi: 192,
  };
  for (const [density, size] of Object.entries(densities)) {
    await sharp(Buffer.from(source))
      .resize(size, size)
      .webp({ lossless: true })
      .toFile(path.join(res, `mipmap-${density}/ic_launcher.webp`));
    await sharp(Buffer.from(round))
      .resize(size, size)
      .webp({ lossless: true })
      .toFile(path.join(res, `mipmap-${density}/ic_launcher_round.webp`));
  }

  const selected = path.join(output, "android/selected-B");
  for (const relative of [
    "drawable/spectra_icon.xml",
    "drawable/spectra_icon_monochrome.xml",
    "drawable/spectra_launcher_foreground.xml",
    "drawable/spectra_launcher_monochrome.xml",
    "mipmap-anydpi-v26/ic_launcher.xml",
    "mipmap-anydpi-v26/ic_launcher_round.xml",
  ]) {
    const target = path.join(selected, relative);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.copyFileSync(path.join(res, relative), target);
  }
  fs.mkdirSync(path.join(selected, "values"), { recursive: true });
  fs.writeFileSync(
    path.join(selected, "values/spectra_icon_colors.xml"),
    `<resources><color name="spectra_icon_background">${background}</color></resources>\n`,
  );
  console.log(`Generated ${version} icon resources from design/app-icon.svg`);
}

main().catch((error: unknown) => {
  console.error(error);
  process.exitCode = 1;
});
