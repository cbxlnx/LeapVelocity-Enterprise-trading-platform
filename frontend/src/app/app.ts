import { Component } from '@angular/core';
import { Router, RouterOutlet, NavigationEnd } from '@angular/router';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { filter, map, startWith } from 'rxjs/operators';
import { HeaderComponent } from './shared/layout/header/header';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, HeaderComponent],
  templateUrl: './app.html',
  styleUrls: ['./app.css'],
})
export class App {
  isAuthRoute$: Observable<boolean>;

  constructor(private router: Router) {
    this.isAuthRoute$ = this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      startWith(null),
      map(() => this.isAuthPath(this.router.url))
    );
  }

  private isAuthPath(url: string): boolean {
    return url.startsWith('/login') || url.startsWith('/register');
  }
}