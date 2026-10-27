const CHANNEL_NAME = 'skytrace-admin-session'

function sessionChannel(): BroadcastChannel | undefined {
  if (typeof window === 'undefined' || typeof BroadcastChannel === 'undefined') return
  try {
    return new BroadcastChannel(CHANNEL_NAME)
  } catch {
    return undefined
  }
}

export function notifySessionChange(): void {
  const channel = sessionChannel()
  try {
    channel?.postMessage({ type: 'session-changed' })
  } catch {
    // Cross-tab notifications are best effort, not part of the login commit.
  } finally {
    channel?.close()
  }
}

export function subscribeToSessionChange(listener: () => void): () => void {
  const channel = sessionChannel()
  if (!channel) return () => {}
  channel.onmessage = event => {
    if (event.data?.type === 'session-changed') listener()
  }
  return () => channel.close()
}
