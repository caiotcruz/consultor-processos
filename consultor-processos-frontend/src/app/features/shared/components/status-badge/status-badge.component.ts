import { Component, Input } from '@angular/core';
import { IonBadge }         from '@ionic/angular/standalone';
import { ProcessStatusPipe } from '../../pipes/process-status.pipe';

@Component({
  selector:   'app-status-badge',
  standalone: true,
  imports:    [IonBadge, ProcessStatusPipe],
  template: `
    <ion-badge [color]="status | processStatus:'color'">
      {{ status | processStatus }}
    </ion-badge>
  `,
  styles: [`
    ion-badge { font-size: 11px; font-weight: 600; letter-spacing: 0.3px; }
  `]
})
export class StatusBadgeComponent {
  @Input() status = 'PENDING';
}