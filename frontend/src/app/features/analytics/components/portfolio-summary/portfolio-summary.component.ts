import { CurrencyPipe, DecimalPipe, PercentPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { PortfolioSummary } from '../../models/portfolio-summary.model';
import { formatTradingDate } from '../../models/trading-date';

@Component({
  selector: 'app-portfolio-summary',
  standalone: true,
  imports: [CurrencyPipe, DecimalPipe, PercentPipe],
  templateUrl: './portfolio-summary.component.html',
  styleUrl: './portfolio-summary.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PortfolioSummaryComponent {
  readonly summary = input<PortfolioSummary | null>(null);
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly refresh = output<void>();
  readonly formatDate = formatTradingDate;
}
