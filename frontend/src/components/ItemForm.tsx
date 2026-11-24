"use client";

import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { api, ApiError, errorMessage } from "@/lib/api";
import { useData } from "@/lib/useData";
import { deliveryMethods } from "@/lib/deliveryMethods";
import type { Category, Condition, DeliveryMethod, District, Item } from "@/lib/types";
import { Conditions, Districts } from "./Fields";
import RequestStatus from "./RequestStatus";
import { useSession } from "./Session";

function FieldTitle({ text, required = true }: { text: string; required?: boolean }) {
  return <span className="listing-label">{text}{required && <span className="listing-required" aria-hidden="true">*</span>}</span>;
}

export default function ItemForm({ initial }: { initial?: Item }) {
  const router = useRouter();
  const categories = useData<Category[]>("/categories");
  const districts = useData<District[]>("/districts");
  const conditions = useData<Condition[]>("/item-conditions");
  const [item, setItem] = useState(initial);
  const [categoryId, setCategoryId] = useState(initial?.category?.id || "");
  const [districtId, setDistrictId] = useState(initial?.district?.id || "");
  const [size, setSize] = useState(initial?.size || "");
  const [deliveryMethod, setDeliveryMethod] = useState<DeliveryMethod>(initial?.deliveryMethod ?? "FREE");
  const [files, setFiles] = useState<File[]>([]);
  const [title, setTitle] = useState(initial?.title ?? "");
  const [previewUrl, setPreviewUrl] = useState("");
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const needsSize = categories.data?.find((category) => category.id === categoryId)?.sizeRequired;
  const photoCount = item?.photos.length || 0;
  const firstFile = files[0];
  useEffect(() => {
    if (!firstFile) return;
    const url = URL.createObjectURL(firstFile);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [firstFile]);

  function showError(failure: unknown) {
    const fields = failure instanceof ApiError ? Object.values(failure.fields) : [];
    setError([errorMessage(failure), ...fields].join(" "));
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setError("");
    setNotice("");
    const submitter = (event.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null;
    const publish = submitter?.value === "publish";
    const data = new FormData(event.currentTarget);
    const value = (name: string) => String(data.get(name) || "").trim();
    if (files.length + photoCount > 5) return setError("Можно добавить не больше пяти фотографий.");
    if (files.some((file) => !["image/jpeg", "image/png", "image/webp"].includes(file.type) || file.size > 5 * 1024 * 1024 || !file.size)) {
      return setError("Выберите JPEG, PNG или WebP размером до 5 МБ.");
    }
    if (publish && (!value("description") || !categoryId || !value("districtId") || !value("condition") || (needsSize && !value("size")) || !(files.length + photoCount))) {
      return setError("Для публикации заполните описание, категорию, район, состояние, размер при необходимости и добавьте фото.");
    }
    const json = {
      title: value("title"),
      description: value("description") || null,
      categoryId: categoryId || null,
      districtId: value("districtId") || null,
      condition: value("condition") || null,
      size: needsSize ? value("size") || null : null,
      deliveryMethod,
    };

    setBusy(true);
    try {
      let saved = await api<Item>(item ? "/items/" + item.id : "/items", { method: item ? "PUT" : "POST", json });
      setItem(saved);
      for (const file of files) {
        const form = new FormData();
        form.append("file", file);
        saved = await api<Item>("/items/" + saved.id + "/photos", { method: "POST", form });
        setItem(saved);
        setFiles((remaining) => remaining.slice(1));
      }
      if (input.current) input.current.value = "";
      if (publish) {
        await api("/items/" + saved.id + "/publish", { method: "POST" });
        router.push("/items/" + saved.id);
      } else {
        setNotice(saved.status === "DRAFT" ? "Черновик сохранён." : "Изменения сохранены.");
      }
    } catch (failure) {
      showError(failure);
    } finally {
      setBusy(false);
    }
  }

  async function removePhoto(id: string) {
    if (!item) return;
    setError("");
    setNotice("");
    setBusy(true);
    try {
      setItem(await api<Item>("/items/" + item.id + "/photos/" + id, { method: "DELETE" }));
    } catch (failure) {
      showError(failure);
    } finally {
      setBusy(false);
    }
  }

  const refs = { loading: categories.loading || districts.loading || conditions.loading, error: categories.error || districts.error || conditions.error };
  if (refs.loading || refs.error) return <RequestStatus {...refs} retry={() => {
    categories.reload(); districts.reload(); conditions.reload();
  }} />;
  const preview = firstFile ? previewUrl : item?.photos[0]?.url;
  const district = districts.data?.find((entry) => entry.id === districtId);
  return <section className="listing-editor">
    <h1>{initial ? "Редактирование объявления" : "Создание объявления"}</h1>
    {!initial && <p>Поделитесь вещью с другими - помогите ей обрести вторую жизнь!</p>}
    <form id="listing-form" onSubmit={save}>
      <fieldset className="listing-fields" disabled={busy}>
        <div className="listing-wide listing-photos">
          <p>Фотографии<span className="listing-required" aria-hidden="true">*</span></p>
          <small>Можно загрузить до 5 фотографий</small>
          <label className="listing-upload">
            <span className="listing-upload-plus" aria-hidden="true">+</span><span>Добавить<br />фото</span>
            <input ref={input} type="file" accept="image/jpeg,image/png,image/webp" multiple onChange={(event) => setFiles(Array.from(event.target.files || []))} />
          </label>
        </div>
        {files.length > 0 && <p className="listing-wide">К загрузке: {files.map((file) => file.name).join(", ")}</p>}
        {item && item.photos.length > 0 && <div className="gallery listing-wide">
          {item.photos.map((photo) => <div key={photo.id}>
            <img src={photo.url} alt="Фото вещи" />
            <button type="button" className="secondary" disabled={item.status === "PUBLISHED" && item.photos.length === 1} onClick={() => removePhoto(photo.id)}>Удалить фото</button>
          </div>)}
        </div>}
        <label className="listing-wide listing-name"><FieldTitle text="Название объявления" /><input name="title" placeholder="Например: Куртка в хорошем состоянии" value={title} onChange={(event) => setTitle(event.target.value)} required maxLength={100} /></label>
        <label><FieldTitle text="Категория" /><select value={categoryId} onChange={(event) => setCategoryId(event.target.value)}>
          <option value="">Выберите категорию</option>
          {categories.data?.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
        </select></label>
        <Conditions name="condition" label={<FieldTitle text="Состояние" />} options={conditions.data} value={initial?.condition || ""} placeholder="Выберите состояние" />
        <div className="listing-transfer">
          <fieldset className="listing-method" aria-describedby="listing-method-hint">
            <legend><FieldTitle text="Способ передачи" /></legend>
            <small id="listing-method-hint">Выберите один способ передачи</small>
            <div className="actions">
              {(Object.keys(deliveryMethods) as DeliveryMethod[]).map((method) => <label key={method} className="secondary">
                <input className="visually-hidden" type="radio" name="deliveryMethod" value={method}
                  checked={deliveryMethod === method} onChange={() => setDeliveryMethod(method)} required />
                <img src={"/design/" + deliveryMethods[method].icon} alt="" />{deliveryMethods[method].label}
              </label>)}
            </div>
          </fieldset>
          <label className="listing-size"><FieldTitle text="Размер" required={Boolean(needsSize)} /><input name="size" placeholder="Выберите свой размер"
            value={size} onChange={(event) => setSize(event.target.value)} maxLength={20} disabled={!needsSize} /></label>
        </div>
        <label className="listing-wide listing-description"><FieldTitle text="Описание" /><textarea name="description" defaultValue={initial?.description || ""} maxLength={2000} rows={5}
          placeholder="Расскажите подробнее о вещи, её особенностях и возможных дефектах..." /></label>
        <Districts name="districtId" label={<FieldTitle text="Район" />} options={districts.data} value={districtId} onChange={setDistrictId} />
      </fieldset>
    </form>
    {(!item || item.status === "DRAFT") && <button form="listing-form" value="save" className="secondary listing-draft" disabled={busy}>
      {busy ? "Сохраняем…" : "Сохранить черновик"}
    </button>}
    <RequestStatus error={error} />
    {notice && <p role="status">{notice} {item && <a href={"/items/" + item.id}>Открыть объявление</a>}</p>}
    <aside className={needsSize ? "panel listing-preview listing-preview-size" : "panel listing-preview"}>
      <h2>Предпросмотр объявления</h2>
      <div className="card">
        <div className="card-image">{preview && <img src={preview} alt="" />}</div>
        <div className="card-body">
          <div className="listing-preview-heading">
            <p>{title.trim() || "Название"}</p>
            <span>{deliveryMethods[deliveryMethod].label}</span>
          </div>
          <div className="listing-preview-location"><img src="/design/pin.svg" alt="" /><p>{district?.name || "Где находится вещь"}</p></div>
          {needsSize && <p>Размер: {size.trim() || "Размер вещи"}</p>}
        </div>
      </div>
      <div className="listing-actions">
        {(!item || item.status === "DRAFT") && <button form="listing-form" value="publish" disabled={busy}>Опубликовать объявление</button>}
        {item?.status === "PUBLISHED" && <button form="listing-form" value="save" disabled={busy}>
          {busy ? "Сохраняем…" : "Сохранить изменения"}
        </button>}
        <Link className="button secondary" href={initial ? "/items/" + initial.id : "/profile/items"}>Отменить</Link>
      </div>
    </aside>
  </section>;
}

export function EditItem({ id }: { id: string }) {
  const { user } = useSession();
  const item = useData<Item>("/items/" + encodeURIComponent(id));
  if (!item.data) return <RequestStatus loading={item.loading} error={item.error} retry={item.reload} />;
  if (item.data.owner.id !== user?.id && user?.role !== "ADMIN") return <RequestStatus error="Редактировать объявление может его владелец." />;
  if (!["DRAFT", "PUBLISHED"].includes(item.data.status)) return <RequestStatus error="Объявление нельзя изменить в текущем состоянии." />;
  return <ItemForm initial={item.data} />;
}
