import { decodeReply, decisionFromReply, encodeCommand } from './auth-redis'

describe('auth redis protocol', () => {
  it('round-trips an integer array reply', () => {
    const encoded = encodeCommand(['EVAL', 'return {1,0}', '0'])
    expect(encoded.toString('utf8')).toContain('EVAL')
    const reply = Buffer.from('*2\r\n:1\r\n:0\r\n')
    expect(decodeReply(reply).value).toEqual([1, 0])
    expect(decisionFromReply([1, 0])).toEqual({ allowed: true, retryAfterSec: 0 })
    expect(decisionFromReply([0, 12])).toEqual({ allowed: false, retryAfterSec: 12 })
  })
})
