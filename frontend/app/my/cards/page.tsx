import { redirect } from 'next/navigation';

export default function MyCards() {
  redirect('/cards?scope=mine');
}
