import { Module } from '@nestjs/common'
import { HttpModule } from '@nestjs/axios'
import { JavaClientService } from './java-client.service'

@Module({
  imports: [
    HttpModule.register({ timeout: 5_000, maxRedirects: 3 }),
  ],
  providers: [JavaClientService],
  exports: [JavaClientService, HttpModule],
})
export class JavaClientModule {}
