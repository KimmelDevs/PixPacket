import React from "react";
import {
  AbsoluteFill,
  Easing,
  Img,
  interpolate,
  OffthreadVideo,
  Sequence,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";

const NAVY = "#08102a";
const BLUE = "#004eb2";
const YELLOW = "#ffd640";
const SKY = "#cddeff";
const DISPLAY = '"Segoe UI Black", "Segoe UI", system-ui, sans-serif';
const TEXT = '"Segoe UI", system-ui, sans-serif';

export const FPS = 30;

/** Recorded phone clips: where the action is (seconds) and how much to speed it up. */
export const SCENES = [
  { clip: "draw", from: 2.0, to: 9.5, rate: 1.3, title: "Draw half.", sub: "Mirror does the rest.", accent: YELLOW },
  { clip: "fill", from: 1.5, to: 11.5, rate: 1.6, title: "Fill in one tap.", sub: "Add shine with a few pixels.", accent: "#ff8fc7" },
  { clip: "palette", from: 2.5, to: 12.5, rate: 1.6, title: "Every palette you love.", sub: "PICO-8, NES, Endesga… or your own.", accent: "#5fe0cf" },
  { clip: "anim", from: 2.5, to: 16.5, rate: 1.8, title: "Bring it to life.", sub: "Frames, onion skin, live preview.", accent: "#ffb066" },
  { clip: "export", from: 2.5, to: 9.5, rate: 1.3, title: "Take it anywhere.", sub: "GIF · PNG · Aseprite · Piskel", accent: "#8ab4ff" },
] as const;

export const INTRO = 96;
export const OUTRO = 150;
const sceneFrames = (s: (typeof SCENES)[number]) => Math.round(((s.to - s.from) / s.rate) * FPS);
export const TOTAL = INTRO + SCENES.reduce((a, s) => a + sceneFrames(s), 0) + OUTRO;

/** Brand background: gradient, a slowly drifting pixel grid and a few floating pixel sparkles. */
const Background: React.FC = () => {
  const frame = useCurrentFrame();
  const shift = (frame * 0.4) % 40;
  const sparkles = [
    [90, 260, YELLOW], [960, 340, "#5fe0cf"], [120, 1500, "#ff8fc7"], [980, 1320, "#8ab4ff"],
    [60, 860, "#ffffff"], [1010, 900, YELLOW], [200, 1760, "#5fe0cf"], [900, 1700, "#ffffff"],
  ] as const;
  return (
    <AbsoluteFill style={{ background: `linear-gradient(180deg, ${NAVY} 0%, ${BLUE} 100%)` }}>
      <AbsoluteFill
        style={{
          backgroundImage:
            "linear-gradient(rgba(255,255,255,0.045) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.045) 1px, transparent 1px)",
          backgroundSize: "40px 40px",
          backgroundPosition: `${shift}px ${shift}px`,
        }}
      />
      {sparkles.map(([x, y, c], i) => {
        const bob = Math.sin((frame + i * 17) / 22) * 10;
        const op = 0.35 + 0.3 * Math.sin((frame + i * 31) / 15);
        return (
          <div key={i} style={{ position: "absolute", left: x, top: y + bob, opacity: op }}>
            <div style={{ position: "absolute", left: 10, top: 0, width: 10, height: 30, background: c }} />
            <div style={{ position: "absolute", left: 0, top: 10, width: 30, height: 10, background: c }} />
          </div>
        );
      })}
    </AbsoluteFill>
  );
};

const LogoBadge: React.FC<{ size: number }> = ({ size }) => (
  <div
    style={{
      width: size, height: size, borderRadius: size * 0.26, background: "#fff",
      display: "flex", alignItems: "center", justifyContent: "center", boxShadow: "0 20px 50px rgba(0,0,0,0.35)",
    }}
  >
    <Img src={staticFile("logo.png")} style={{ width: size * 0.8, height: size * 0.8, objectFit: "contain" }} />
  </div>
);

/** Fades a scene in and out over a few frames. */
const useSceneFade = (duration: number, edge = 8) => {
  const frame = useCurrentFrame();
  return interpolate(frame, [0, edge, duration - edge, duration], [0, 1, 1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
};

const Intro: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const pop = spring({ frame, fps, config: { damping: 11, stiffness: 140 } });
  const word = spring({ frame: frame - 10, fps, config: { damping: 16 } });
  const tag = spring({ frame: frame - 26, fps, config: { damping: 18 } });
  const chip = spring({ frame: frame - 42, fps, config: { damping: 12 } });
  const fade = useSceneFade(INTRO, 10);
  return (
    <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", opacity: fade }}>
      <div style={{ transform: `scale(${pop})` }}>
        <LogoBadge size={260} />
      </div>
      <div
        style={{
          marginTop: 50, fontFamily: DISPLAY, fontWeight: 900, fontSize: 150, color: "white",
          opacity: word, transform: `translateY(${(1 - word) * 60}px)`, letterSpacing: -2,
        }}
      >
        PixPacket
      </div>
      <div
        style={{
          marginTop: 10, fontFamily: DISPLAY, fontWeight: 900, fontSize: 64, color: YELLOW, textAlign: "center",
          opacity: tag, transform: `translateY(${(1 - tag) * 40}px)`,
        }}
      >
        A pixel editor in your pocket.
      </div>
      <div
        style={{
          marginTop: 46, padding: "14px 34px", borderRadius: 60, background: YELLOW, color: NAVY,
          fontFamily: "Consolas, monospace", fontWeight: 700, fontSize: 40, letterSpacing: 4,
          transform: `scale(${chip})`,
        }}
      >
        EARLY ACCESS
      </div>
    </AbsoluteFill>
  );
};

const PhoneClip: React.FC<{ scene: (typeof SCENES)[number]; duration: number; index: number }> = ({ scene, duration, index }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const enter = spring({ frame, fps, config: { damping: 15, stiffness: 120 } });
  const capIn = spring({ frame: frame - 4, fps, config: { damping: 18 } });
  const fade = useSceneFade(duration);
  const phoneH = 1240;
  const phoneW = Math.round((phoneH * 720) / 1612);
  const zoom = interpolate(frame, [0, duration], [1, 1.04], { easing: Easing.inOut(Easing.quad) });
  return (
    <AbsoluteFill style={{ opacity: fade }}>
      {/* Caption */}
      <div style={{ position: "absolute", top: 150, width: "100%", textAlign: "center", opacity: capIn, transform: `translateY(${(1 - capIn) * -40}px)` }}>
        <div style={{ fontFamily: DISPLAY, fontWeight: 900, fontSize: 92, color: "white", letterSpacing: -1 }}>{scene.title}</div>
        <div style={{ fontFamily: TEXT, fontSize: 44, color: SKY, marginTop: 6 }}>{scene.sub}</div>
      </div>
      {/* Phone */}
      <div
        style={{
          position: "absolute", left: (1080 - phoneW - 28) / 2, top: 500,
          transform: `translateY(${(1 - enter) * 300}px) scale(${zoom})`, transformOrigin: "50% 40%",
          padding: 14, borderRadius: 64, background: "#101016", border: "3px solid #505c82",
          boxShadow: `0 40px 90px rgba(0,0,0,0.5), 0 0 0 10px ${scene.accent}22`,
        }}
      >
        <div style={{ width: phoneW, height: phoneH, borderRadius: 50, overflow: "hidden", background: "#000" }}>
          <OffthreadVideo
            src={staticFile(`clips/${scene.clip}.mp4`)}
            startFrom={Math.round(scene.from * FPS)}
            playbackRate={scene.rate}
            muted
            style={{ width: phoneW, height: phoneH }}
          />
        </div>
      </div>
      {/* Progress pixels */}
      <div style={{ position: "absolute", bottom: 70, width: "100%", display: "flex", justifyContent: "center", gap: 14 }}>
        {SCENES.map((s, i) => (
          <div key={i} style={{ width: i === index ? 40 : 16, height: 16, background: i === index ? scene.accent : "rgba(255,255,255,0.35)" }} />
        ))}
      </div>
    </AbsoluteFill>
  );
};

const Outro: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const a = spring({ frame, fps, config: { damping: 14 } });
  const b = spring({ frame: frame - 12, fps, config: { damping: 14 } });
  const c = spring({ frame: frame - 26, fps, config: { damping: 12 } });
  const d = spring({ frame: frame - 44, fps, config: { damping: 18 } });
  const fadeOut = interpolate(frame, [OUTRO - 12, OUTRO], [1, 0], { extrapolateLeft: "clamp" });
  const pulse = 1 + 0.03 * Math.sin(frame / 6);
  return (
    <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", opacity: fadeOut }}>
      <div style={{ transform: `scale(${a})` }}>
        <LogoBadge size={220} />
      </div>
      <div style={{ marginTop: 44, fontFamily: DISPLAY, fontWeight: 900, fontSize: 130, color: "white", opacity: a, letterSpacing: -2 }}>PixPacket</div>
      <div
        style={{
          marginTop: 30, padding: "14px 34px", borderRadius: 60, background: YELLOW, color: NAVY,
          fontFamily: "Consolas, monospace", fontWeight: 700, fontSize: 38, letterSpacing: 4,
          transform: `scale(${b * pulse})`,
        }}
      >
        EARLY ACCESS
      </div>
      <div style={{ marginTop: 40, textAlign: "center", opacity: c, transform: `translateY(${(1 - c) * 40}px)` }}>
        <div style={{ fontFamily: DISPLAY, fontWeight: 900, fontSize: 84, color: "white", lineHeight: 1.05 }}>Coming soon to</div>
        <div style={{ fontFamily: DISPLAY, fontWeight: 900, fontSize: 96, color: YELLOW, lineHeight: 1.1 }}>Google Play</div>
      </div>
      <div style={{ marginTop: 50, textAlign: "center", opacity: d }}>
        <div style={{ fontFamily: TEXT, fontSize: 40, color: SKY }}>Pixel art &amp; animation for Android</div>
        <div style={{ fontFamily: TEXT, fontWeight: 600, fontSize: 38, color: "white", marginTop: 14 }}>kimmeldevs.github.io/PixPacket</div>
      </div>
    </AbsoluteFill>
  );
};

export const Promo: React.FC = () => {
  let at = INTRO;
  return (
    <AbsoluteFill style={{ background: NAVY }}>
      <Background />
      <Sequence durationInFrames={INTRO}>
        <Intro />
      </Sequence>
      {SCENES.map((s, i) => {
        const dur = sceneFrames(s);
        const seq = (
          <Sequence key={s.clip} from={at} durationInFrames={dur}>
            <PhoneClip scene={s} duration={dur} index={i} />
          </Sequence>
        );
        at += dur;
        return seq;
      })}
      <Sequence from={at} durationInFrames={OUTRO}>
        <Outro />
      </Sequence>
    </AbsoluteFill>
  );
};
