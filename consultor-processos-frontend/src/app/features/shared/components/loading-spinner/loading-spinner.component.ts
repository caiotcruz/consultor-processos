import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonSpinner } from '@ionic/angular/standalone';

@Component({
  selector: 'app-loading-spinner',
  standalone: true,
  imports: [CommonModule, IonSpinner],
  templateUrl: './loading-spinner.component.html',
  styleUrls: ['./loading-spinner.component.scss']
})
export class LoadingSpinnerComponent {
  @Input() message = '';
  @Input() spinnerName = 'crescent';
}