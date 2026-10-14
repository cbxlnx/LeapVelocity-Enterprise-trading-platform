import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-signed-out',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './signed-out.component.html',
  styleUrl: './signed-out.component.css',
})
export class SignedOutComponent {}
