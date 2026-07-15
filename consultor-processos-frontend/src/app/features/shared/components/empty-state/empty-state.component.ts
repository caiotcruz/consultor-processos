import { Component, Input }      from '@angular/core';
import { CommonModule }          from '@angular/common';
import { RouterLink }            from '@angular/router';
import { IonIcon, IonButton }    from '@ionic/angular/standalone';
import { addIcons }              from 'ionicons';
import {
  documentTextOutline, alertCircleOutline,
  searchOutline, notificationsOffOutline
} from 'ionicons/icons';

@Component({
  selector:    'app-empty-state',
  standalone:  true,
  imports:     [CommonModule, RouterLink, IonIcon, IonButton],
  template: `
    <div class="empty-state">
      <ion-icon [name]="icon" class="empty-icon" color="medium"></ion-icon>
      <h3 class="empty-title">{{ title }}</h3>
      <p  class="empty-message" *ngIf="message">{{ message }}</p>
      <ion-button
        *ngIf="actionLabel && actionLink"
        [routerLink]="actionLink"
        fill="outline"
        class="ion-margin-top">
        {{ actionLabel }}
      </ion-button>
    </div>
  `,
  styles: [`
    .empty-state {
      display:         flex;
      flex-direction:  column;
      align-items:     center;
      justify-content: center;
      padding:         48px 24px;
      text-align:      center;
      min-height:      280px;
    }
    .empty-icon  { font-size: 64px; margin-bottom: 16px; opacity: 0.5; }
    .empty-title { font-size: 18px; font-weight: 600; margin: 0 0 8px; color: #444; }
    .empty-message { color: #888; font-size: 14px; max-width: 280px; line-height: 1.5; }
  `]
})
export class EmptyStateComponent {
  @Input() icon        = 'document-text-outline';
  @Input() title       = 'Nenhum item encontrado';
  @Input() message     = '';
  @Input() actionLabel = '';
  @Input() actionLink  = '';

  constructor() {
    addIcons({ documentTextOutline, alertCircleOutline, searchOutline, notificationsOffOutline });
  }
}