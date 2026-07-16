import { Component, Input } from '@angular/core';
import { ProcessStatusPipe } from '../../pipes/process-status.pipe';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [ProcessStatusPipe],
  templateUrl: './status-badge.component.html',
  styleUrls: ['./status-badge.component.scss']
})
export class StatusBadgeComponent {
  @Input() status = 'PENDING';
}