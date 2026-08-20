const LETTER = /[A-Za-z]/;
const DIGIT = /[0-9]/;

/** R4.1 portal password: ≥8 and both letter and digit. */
export function portalPasswordSatisfied(password: string): boolean {
  return password.length >= 8 && LETTER.test(password) && DIGIT.test(password);
}
