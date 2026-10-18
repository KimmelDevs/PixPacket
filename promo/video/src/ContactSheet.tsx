import { AbsoluteFill, OffthreadVideo, staticFile } from "remotion";

/** Dev helper: one still showing every clip at half-second steps, to choose trim points. */
const clips = ["draw", "fill", "palette", "anim", "export"];
const STEPS = 17; // 0s .. 16s
const W = 80;
const H = Math.round((W * 1612) / 720);

export const ContactSheet: React.FC = () => (
  <AbsoluteFill style={{ background: "#111", display: "flex", flexDirection: "column", gap: 6, padding: 6 }}>
    {clips.map((c) => (
      <div key={c} style={{ display: "flex", gap: 4 }}>
        {Array.from({ length: STEPS }, (_, i) => (
          <div key={i} style={{ position: "relative", width: W, height: H }}>
            <OffthreadVideo src={staticFile(`clips/${c}.mp4`)} startFrom={i * 30} style={{ width: W, height: H }} muted />
            <div style={{ position: "absolute", top: 0, left: 0, background: "#000a", color: "#ff0", fontSize: 14, padding: "0 3px", fontFamily: "monospace" }}>
              {i}s
            </div>
          </div>
        ))}
      </div>
    ))}
  </AbsoluteFill>
);
