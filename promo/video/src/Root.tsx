import { Composition } from "remotion";
import { ContactSheet } from "./ContactSheet";
import { FPS, Promo, TOTAL } from "./Promo";

export const RemotionRoot: React.FC = () => (
  <>
    <Composition id="PixPacketPromo" component={Promo} durationInFrames={TOTAL} fps={FPS} width={1080} height={1920} />
    {/* Dev helper for choosing trim points */}
    <Composition id="ContactSheet" component={ContactSheet} durationInFrames={1} fps={30} width={2900} height={1000} />
  </>
);
