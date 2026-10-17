/**
 * @returns the items with the one whose ID matches changed, and the rest as they were
 */
export function updateById<T extends { id: string }>(items: T[], id: string, change: (item: T) => T): T[] {
  return items.map((item) => {
    if (item.id !== id) {
      return item;
    }
    return change(item);
  });
}
