import { useState } from 'react'
import toast from 'react-hot-toast'
import { api, errorMessage } from '@/lib/api'
import { useAuth } from '@/stores/auth'

export default function Profile() {
  const user = useAuth((state) => state.user)
  const setUser = useAuth((state) => state.setUser)

  const [name, setName] = useState(user?.name ?? '')
  const [phone, setPhone] = useState(user?.phone ?? '')
  const [savingProfile, setSavingProfile] = useState(false)

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [savingPassword, setSavingPassword] = useState(false)

  async function saveProfile(event: React.FormEvent) {
    event.preventDefault()
    setSavingProfile(true)
    try {
      setUser(await api.auth.updateProfile({ name: name.trim(), phone: phone.trim() }))
      toast.success('Profile updated')
    } catch (error) {
      toast.error(errorMessage(error, 'Could not save your profile'))
    } finally {
      setSavingProfile(false)
    }
  }

  async function savePassword(event: React.FormEvent) {
    event.preventDefault()
    if (newPassword.length < 8) {
      toast.error('New password must be at least 8 characters')
      return
    }
    setSavingPassword(true)
    try {
      await api.auth.changePassword(currentPassword, newPassword)
      setCurrentPassword('')
      setNewPassword('')
      toast.success('Password changed')
    } catch (error) {
      toast.error(errorMessage(error, 'Could not change your password'))
    } finally {
      setSavingPassword(false)
    }
  }

  return (
    <section className="section">
      <div className="container grid grid-2" style={{ alignItems: 'start' }}>
        <form className="card" onSubmit={saveProfile}>
          <h2 style={{ fontSize: '1.1rem' }}>Your details</h2>

          <div className="field">
            <label htmlFor="name">Full name</label>
            <input id="name" value={name} onChange={(event) => setName(event.target.value)} />
          </div>

          <div className="field">
            <label htmlFor="email">Email</label>
            {/* Changing the login email is a support action here, not a self-service one. */}
            <input id="email" value={user?.email ?? ''} disabled />
          </div>

          <div className="field">
            <label htmlFor="phone">Phone</label>
            <input id="phone" value={phone} onChange={(event) => setPhone(event.target.value)} />
          </div>

          <button type="submit" className="btn" disabled={savingProfile}>
            {savingProfile ? <span className="spinner" /> : 'Save changes'}
          </button>
        </form>

        <form className="card" onSubmit={savePassword}>
          <h2 style={{ fontSize: '1.1rem' }}>Password</h2>

          <div className="field">
            <label htmlFor="currentPassword">Current password</label>
            <input
              id="currentPassword"
              type="password"
              required
              autoComplete="current-password"
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
            />
          </div>

          <div className="field">
            <label htmlFor="newPassword">New password</label>
            <input
              id="newPassword"
              type="password"
              required
              minLength={8}
              autoComplete="new-password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
            />
          </div>

          <button type="submit" className="btn" disabled={savingPassword}>
            {savingPassword ? <span className="spinner" /> : 'Change password'}
          </button>
        </form>
      </div>
    </section>
  )
}
