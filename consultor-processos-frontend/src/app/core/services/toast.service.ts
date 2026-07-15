import { Injectable } from '@angular/core';
import { ToastController } from '@ionic/angular';

@Injectable({ providedIn: 'root' })
export class ToastService {

  constructor(private toastCtrl: ToastController) {}

  async success(message: string, duration = 2500): Promise<void> {
    await this.show(message, 'success', duration);
  }

  async error(message: string, duration = 3500): Promise<void> {
    await this.show(message, 'danger', duration);
  }

  async info(message: string, duration = 2500): Promise<void> {
    await this.show(message, 'medium', duration);
  }

  private async show(message: string, color: string, duration: number): Promise<void> {
    const toast = await this.toastCtrl.create({
      message, color, duration,
      position: 'top',
      buttons: [{ icon: 'close', role: 'cancel' }]
    });
    await toast.present();
  }
}