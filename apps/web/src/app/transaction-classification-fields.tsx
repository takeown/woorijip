"use client";

import { useState } from "react";
import {
  transactionCategories,
  transactionTags,
  type TransactionCategory,
  type TransactionTag,
} from "./transaction-classification";

type TransactionClassificationFieldsProps = {
  idPrefix: string;
  defaultCategory?: TransactionCategory;
  defaultTags?: TransactionTag[];
};

export function TransactionClassificationFields({
  idPrefix,
  defaultCategory,
  defaultTags = [],
}: TransactionClassificationFieldsProps) {
  const [category, setCategory] = useState<TransactionCategory | "">(
    defaultCategory ?? "",
  );
  const selectedCategory = transactionCategories.find(
    (item) => item.value === category,
  );

  return (
    <>
      <div>
        <label
          className="mb-2 block text-sm font-medium text-stone-700"
          htmlFor={`${idPrefix}-category`}
        >
          카테고리
        </label>
        <select
          className="w-full rounded-xl border border-stone-300 bg-white px-4 py-3 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
          id={`${idPrefix}-category`}
          name="category"
          onChange={(event) =>
            setCategory(event.target.value as TransactionCategory)
          }
          required
          value={category}
        >
          <option disabled value="">카테고리를 선택해 주세요</option>
          {transactionCategories.map((item) => (
            <option key={item.value} value={item.value}>
              {item.label} — {item.examples.slice(0, 3).join("·")}
            </option>
          ))}
        </select>
        {selectedCategory ? (
          <details className="mt-2 text-xs text-stone-600">
            <summary className="cursor-pointer">
              {selectedCategory.label}에는 어떤 지출이 들어가나요?
            </summary>
            <p className="mt-2 leading-5">
              {selectedCategory.examples.join(", ")}
            </p>
          </details>
        ) : (
          <p className="mt-2 text-xs text-stone-500">
            항목을 선택하면 대표 지출 예시를 볼 수 있습니다.
          </p>
        )}
      </div>

      <fieldset>
        <legend className="mb-2 text-sm font-medium text-stone-700">
          태그 <span className="font-normal text-stone-500">(복수 선택 가능)</span>
        </legend>
        <div className="flex flex-wrap gap-2">
          {transactionTags.map((tag) => (
            <label
              className="cursor-pointer rounded-full border border-stone-300 bg-white px-3 py-2 text-sm text-stone-700 has-checked:border-emerald-600 has-checked:bg-emerald-50 has-checked:text-emerald-800"
              key={tag.value}
            >
              <input
                className="sr-only"
                defaultChecked={defaultTags.includes(tag.value)}
                name="tags"
                type="checkbox"
                value={tag.value}
              />
              {tag.label}
            </label>
          ))}
        </div>
      </fieldset>
    </>
  );
}
