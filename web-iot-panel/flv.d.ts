declare module 'flv.js' {
  export interface FlvPlayer {
    attachMediaElement(element: HTMLMediaElement): void;
    load(): void;
    play(): Promise<void>;
    destroy(): void;
  }
  export function createPlayer(mediaDataSource: { type: string; url: string }, config?: { isLive?: boolean }): FlvPlayer;
  export function isSupported(): boolean;
}
