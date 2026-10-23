import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { GeometricMarketIllustrationComponent } from './geometric-market-illustration.component';
import { WELCOME_CONTENT } from './welcome-content';

@Component({
  selector: 'app-welcome-page',
  standalone: true,
  imports: [RouterLink, GeometricMarketIllustrationComponent],
  templateUrl: './welcome-page.component.html',
  styleUrl: './welcome-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WelcomePageComponent {
  readonly content = WELCOME_CONTENT;
}
