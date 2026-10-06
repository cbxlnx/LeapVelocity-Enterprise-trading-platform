import "reflect-metadata";
import { NestFactory } from "@nestjs/core";
import { ValidationPipe } from "@nestjs/common";
import { AppModule } from "./app.module";

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true }));
  const port = Number(process.env.PORT ?? 4000);
  await app.listen(port);
  console.log(`Listening on ${await app.getUrl()}`);
}

bootstrap();
