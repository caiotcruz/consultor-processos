import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { IonIcon } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  documentTextOutline, alertCircleOutline,
  searchOutline, notificationsOffOutline
} from 'ionicons/icons';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [CommonModule, RouterLink, IonIcon],
  templateUrl: './empty-state.component.html',
  styleUrls: ['./empty-state.component.scss']
})
export class EmptyStateComponent {
  @Input() icon = 'document-text-outline';
  @Input() title = 'Nenhum item encontrado';
  @Input() message = '';
  @Input() actionLabel = '';
  @Input() actionLink = '';

  constructor() {
    addIcons({ documentTextOutline, alertCircleOutline, searchOutline, notificationsOffOutline });
  }
}