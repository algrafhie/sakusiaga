import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'id.sakusiaga.app',
  appName: 'SakuSiaga',
  webDir: 'www',
  android: {
    allowMixedContent: false,
    captureInput: false,
  },
};

export default config;
