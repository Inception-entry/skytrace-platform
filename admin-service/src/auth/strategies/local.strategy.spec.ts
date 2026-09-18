import { UnauthorizedException } from '@nestjs/common'
import { AuthService } from '../auth.service'
import { LocalStrategy } from './local.strategy'

describe('LocalStrategy', () => {
  const authService = { validateUser: jest.fn() }
  const strategy = new LocalStrategy(authService as unknown as AuthService)

  beforeEach(() => {
    authService.validateUser.mockReset()
  })

  it('returns the user when credentials are valid', async () => {
    const user = { id: 1, username: 'admin', status: 1 }
    authService.validateUser.mockResolvedValue(user)
    await expect(strategy.validate('admin', 'secret')).resolves.toBe(user)
  })

  it('maps a null validation result to the same 401 message', async () => {
    authService.validateUser.mockResolvedValue(null)
    await expect(strategy.validate('nobody', 'pass')).rejects.toBeInstanceOf(UnauthorizedException)
    await expect(strategy.validate('nobody', 'pass')).rejects.toThrow('用户名或密码错误')
  })
})
