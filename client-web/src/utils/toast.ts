import { showToast as vantToast } from 'vant'
import 'vant/es/toast/style'

function toast(message: string, className: string, duration = 2500) {
  vantToast({
    message,
    className,
    position: 'top',
    duration: className === 'toast-fail' ? 3000 : duration,
    wordBreak: 'break-word',
    teleport: 'body',
  })
}

export const showToast = {
  success: (msg: string) => toast(msg, 'toast-success'),
  fail: (msg: string) => toast(msg, 'toast-fail'),
  info: (msg: string) => toast(msg, 'toast-info'),
}
