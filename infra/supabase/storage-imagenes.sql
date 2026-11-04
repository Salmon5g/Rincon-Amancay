begin;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('amancay-originales', 'amancay-originales', false, 5242880, array['image/jpeg', 'image/png', 'image/webp']),
  ('amancay-catalogo', 'amancay-catalogo', true, 5242880, array['image/webp'])
on conflict (id) do update set
  name = excluded.name,
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

do $$
declare
  policy record;
begin
  for policy in
    select policyname
    from pg_policies
    where schemaname = 'storage'
      and tablename = 'objects'
      and left(policyname, 8) = 'amancay_'
  loop
    execute format('drop policy %I on storage.objects', policy.policyname);
  end loop;
end;
$$;

create policy amancay_restringe_storage_cuenta_tecnica
  on storage.objects
  as restrictive
  for all
  to public
  using (
    bucket_id not in ('amancay-originales', 'amancay-catalogo')
    or auth.uid() = '4a91b0f5-72ec-432a-ae3c-007a7eed1b8f'::uuid
  )
  with check (
    bucket_id not in ('amancay-originales', 'amancay-catalogo')
    or auth.uid() = '4a91b0f5-72ec-432a-ae3c-007a7eed1b8f'::uuid
  );

create policy amancay_api_guarda_originales
  on storage.objects
  for insert
  to authenticated
  with check (
    bucket_id = 'amancay-originales'
    and auth.uid() = '4a91b0f5-72ec-432a-ae3c-007a7eed1b8f'::uuid
    and (
      (
        (storage.foldername(name))[1] = 'tiendas'
        and (storage.foldername(name))[2] ~ '^[A-Za-z0-9_-]{1,128}$'
        and (storage.foldername(name))[3] = 'productos'
        and (storage.foldername(name))[4] ~ '^[A-Za-z0-9_-]{1,128}$'
        and array_length(storage.foldername(name), 1) = 4
        and storage.filename(name) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.(jpg|png|webp)$'
      ) or (
        (storage.foldername(name))[1] = 'tiendas'
        and (storage.foldername(name))[2] ~ '^[A-Za-z0-9_-]{1,128}$'
        and (storage.foldername(name))[3] = 'tienda'
        and array_length(storage.foldername(name), 1) = 3
        and storage.filename(name) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.(jpg|png|webp)$'
      ) or (
        (storage.foldername(name))[1] = 'asociaciones'
        and (storage.foldername(name))[2] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
        and (storage.foldername(name))[3] = 'logo'
        and array_length(storage.foldername(name), 1) = 3
        and storage.filename(name) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.(jpg|png|webp)$'
      )
    )
  );

create policy amancay_api_lee_originales
  on storage.objects
  for select
  to authenticated
  using (
    bucket_id = 'amancay-originales'
    and auth.uid() = '4a91b0f5-72ec-432a-ae3c-007a7eed1b8f'::uuid
    and (
      (
        (storage.foldername(name))[1] = 'tiendas'
        and (storage.foldername(name))[2] ~ '^[A-Za-z0-9_-]{1,128}$'
        and (storage.foldername(name))[3] = 'productos'
        and (storage.foldername(name))[4] ~ '^[A-Za-z0-9_-]{1,128}$'
        and array_length(storage.foldername(name), 1) = 4
        and storage.filename(name) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.(jpg|png|webp)$'
      ) or (
        (storage.foldername(name))[1] = 'tiendas'
        and (storage.foldername(name))[2] ~ '^[A-Za-z0-9_-]{1,128}$'
        and (storage.foldername(name))[3] = 'tienda'
        and array_length(storage.foldername(name), 1) = 3
        and storage.filename(name) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.(jpg|png|webp)$'
      ) or (
        (storage.foldername(name))[1] = 'asociaciones'
        and (storage.foldername(name))[2] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
        and (storage.foldername(name))[3] = 'logo'
        and array_length(storage.foldername(name), 1) = 3
        and storage.filename(name) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.(jpg|png|webp)$'
      )
    )
  );

create policy amancay_api_borra_originales
  on storage.objects
  for delete
  to authenticated
  using (
    bucket_id = 'amancay-originales'
    and auth.uid() = '4a91b0f5-72ec-432a-ae3c-007a7eed1b8f'::uuid
    and (
      (
        (storage.foldername(name))[1] = 'tiendas'
        and (storage.foldername(name))[2] ~ '^[A-Za-z0-9_-]{1,128}$'
        and (storage.foldername(name))[3] = 'productos'
        and (storage.foldername(name))[4] ~ '^[A-Za-z0-9_-]{1,128}$'
        and array_length(storage.foldername(name), 1) = 4
        and storage.filename(name) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.(jpg|png|webp)$'
      ) or (
        (storage.foldername(name))[1] = 'tiendas'
        and (storage.foldername(name))[2] ~ '^[A-Za-z0-9_-]{1,128}$'
        and (storage.foldername(name))[3] = 'tienda'
        and array_length(storage.foldername(name), 1) = 3
        and storage.filename(name) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.(jpg|png|webp)$'
      ) or (
        (storage.foldername(name))[1] = 'asociaciones'
        and (storage.foldername(name))[2] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
        and (storage.foldername(name))[3] = 'logo'
        and array_length(storage.foldername(name), 1) = 3
        and storage.filename(name) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.(jpg|png|webp)$'
      )
    )
  );

create policy amancay_api_publica_derivadas
  on storage.objects
  for insert
  to authenticated
  with check (
    bucket_id = 'amancay-catalogo'
    and auth.uid() = '4a91b0f5-72ec-432a-ae3c-007a7eed1b8f'::uuid
    and (
      ((storage.foldername(name))[1] = 'catalogo' and (storage.foldername(name))[2] ~ '^[A-Za-z0-9_-]{1,128}$' and (storage.foldername(name))[3] = 'productos' and (storage.foldername(name))[4] ~ '^[A-Za-z0-9_-]{1,128}$' and array_length(storage.foldername(name), 1) = 4)
      or ((storage.foldername(name))[1] = 'catalogo' and (storage.foldername(name))[2] ~ '^[A-Za-z0-9_-]{1,128}$' and (storage.foldername(name))[3] = 'tienda' and array_length(storage.foldername(name), 1) = 3)
      or ((storage.foldername(name))[1] = 'catalogo' and (storage.foldername(name))[2] = 'asociaciones' and (storage.foldername(name))[3] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' and (storage.foldername(name))[4] = 'logo' and array_length(storage.foldername(name), 1) = 4)
    )
    and storage.filename(name) ~ '^[0-9a-f]{64}_(1600|320)\.webp$'
  );

create policy amancay_api_lee_derivadas
  on storage.objects
  for select
  to authenticated
  using (
    bucket_id = 'amancay-catalogo'
    and auth.uid() = '4a91b0f5-72ec-432a-ae3c-007a7eed1b8f'::uuid
    and (
      ((storage.foldername(name))[1] = 'catalogo' and (storage.foldername(name))[2] ~ '^[A-Za-z0-9_-]{1,128}$' and (storage.foldername(name))[3] = 'productos' and (storage.foldername(name))[4] ~ '^[A-Za-z0-9_-]{1,128}$' and array_length(storage.foldername(name), 1) = 4)
      or ((storage.foldername(name))[1] = 'catalogo' and (storage.foldername(name))[2] ~ '^[A-Za-z0-9_-]{1,128}$' and (storage.foldername(name))[3] = 'tienda' and array_length(storage.foldername(name), 1) = 3)
      or ((storage.foldername(name))[1] = 'catalogo' and (storage.foldername(name))[2] = 'asociaciones' and (storage.foldername(name))[3] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' and (storage.foldername(name))[4] = 'logo' and array_length(storage.foldername(name), 1) = 4)
    )
    and storage.filename(name) ~ '^[0-9a-f]{64}_(1600|320)\.webp$'
  );

create policy amancay_api_retira_derivadas
  on storage.objects
  for delete
  to authenticated
  using (
    bucket_id = 'amancay-catalogo'
    and auth.uid() = '4a91b0f5-72ec-432a-ae3c-007a7eed1b8f'::uuid
    and (
      ((storage.foldername(name))[1] = 'catalogo' and (storage.foldername(name))[2] ~ '^[A-Za-z0-9_-]{1,128}$' and (storage.foldername(name))[3] = 'productos' and (storage.foldername(name))[4] ~ '^[A-Za-z0-9_-]{1,128}$' and array_length(storage.foldername(name), 1) = 4)
      or ((storage.foldername(name))[1] = 'catalogo' and (storage.foldername(name))[2] ~ '^[A-Za-z0-9_-]{1,128}$' and (storage.foldername(name))[3] = 'tienda' and array_length(storage.foldername(name), 1) = 3)
      or ((storage.foldername(name))[1] = 'catalogo' and (storage.foldername(name))[2] = 'asociaciones' and (storage.foldername(name))[3] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' and (storage.foldername(name))[4] = 'logo' and array_length(storage.foldername(name), 1) = 4)
    )
    and storage.filename(name) ~ '^[0-9a-f]{64}_(1600|320)\.webp$'
  );

commit;
