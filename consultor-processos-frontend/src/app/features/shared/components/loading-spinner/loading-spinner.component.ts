import { Component, Input } from '@angular/core';
import { CommonModule }     from '@angular/common';
import { IonSpinner }       from '@ionic/angular/standalone';

@Component({
  selector:   'app-loading-spinner',
  standalone: true,
  imports:    [CommonModule, IonSpinner],
  template: `
    <div class="spinner-container">
      <ion-spinner [name]="spinnerName" color="primary"></ion-spinner>
      <p *ngIf="message" class="spinner-message">{{ message }}</p>
    </div>
  `,
  styles: [`
    .spinner-container {
      display:         flex;
      flex-direction:  column;
      align-items:     center;
      justify-content: center;
      padding:         48px 16px;
      gap:             12px;
    }
    .spinner-message {
      color:      var(--ion-color-medium);
      font-size:  14px;
      margin:     0;
    }
  `]
})
export class LoadingSpinnerComponent {
  @Input() message     = '';
  @Input() spinnerName = 'crescent';
}