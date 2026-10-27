import { afterEach, describe, expect, it, vi } from 'vitest'
import { notifySessionChange, subscribeToSessionChange } from './sessionSignals'

afterEach(() => { vi.unstubAllGlobals() })

describe('cross-tab session invalidation', () => {
  it('sends only an invalidation signal and closes the channel', () => {
    const postMessage = vi.fn()
    const close = vi.fn()
    vi.stubGlobal('window', {})
    vi.stubGlobal('BroadcastChannel', class { postMessage = postMessage; close = close })
    notifySessionChange()
    expect(postMessage).toHaveBeenCalledWith({ type: 'session-changed' })
    expect(close).toHaveBeenCalledOnce()
  })

  it('invalidates peer state and unsubscribes when the application unmounts', () => {
    let channel!: { onmessage?: (event: { data: unknown }) => void }
    const close = vi.fn()
    vi.stubGlobal('window', {})
    vi.stubGlobal('BroadcastChannel', class {
      onmessage?: (event: { data: unknown }) => void
      close = close
      constructor() { channel = this }
    })
    const listener = vi.fn()
    const unsubscribe = subscribeToSessionChange(listener)
    channel.onmessage?.({ data: { type: 'session-changed' } })
    expect(listener).toHaveBeenCalledOnce()
    unsubscribe()
    expect(close).toHaveBeenCalledOnce()
  })

  it('does not break login when a notification cannot be sent', () => {
    const close = vi.fn()
    vi.stubGlobal('window', {})
    vi.stubGlobal('BroadcastChannel', class {
      postMessage() { throw new Error('channel unavailable') }
      close = close
    })
    expect(() => notifySessionChange()).not.toThrow()
    expect(close).toHaveBeenCalledOnce()
  })

  it('degrades safely when the browser disables BroadcastChannel', () => {
    vi.stubGlobal('window', {})
    vi.stubGlobal('BroadcastChannel', class {
      constructor() { throw new Error('disabled') }
    })
    expect(() => notifySessionChange()).not.toThrow()
    expect(() => subscribeToSessionChange(vi.fn())()).not.toThrow()
  })
})
