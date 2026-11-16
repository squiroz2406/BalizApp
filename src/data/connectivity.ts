import NetInfo from '@react-native-community/netinfo';

/** true mientras el teléfono tiene una red con acceso a internet; avisa cada vez que cambia. */
export function observeOnline(cb: (online: boolean) => void): () => void {
  return NetInfo.addEventListener((s) => cb(s.isConnected !== false && s.isInternetReachable !== false));
}

export async function isOnline(): Promise<boolean> {
  const s = await NetInfo.fetch();
  return s.isConnected !== false && s.isInternetReachable !== false;
}
