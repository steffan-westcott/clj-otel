--
-- PostgreSQL database dump
--

-- Dumped from database version 16.2
-- Dumped by pg_dump version 16.2

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: character; Type: TABLE; Schema: public; Owner: rpg
--

CREATE TABLE public."character" (
    id integer NOT NULL,
    name character varying(80)
);


ALTER TABLE public."character" OWNER TO rpg;

--
-- Name: character_id_seq; Type: SEQUENCE; Schema: public; Owner: rpg
--

CREATE SEQUENCE public.character_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.character_id_seq OWNER TO rpg;

--
-- Name: character_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: rpg
--

ALTER SEQUENCE public.character_id_seq OWNED BY public."character".id;


--
-- Name: item; Type: TABLE; Schema: public; Owner: rpg
--

CREATE TABLE public.item (
    id integer NOT NULL,
    carried_by integer,
    description character varying(80),
    weight integer
);


ALTER TABLE public.item OWNER TO rpg;

--
-- Name: item_id_seq; Type: SEQUENCE; Schema: public; Owner: rpg
--

CREATE SEQUENCE public.item_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.item_id_seq OWNER TO rpg;

--
-- Name: item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: rpg
--

ALTER SEQUENCE public.item_id_seq OWNED BY public.item.id;


--
-- Name: character id; Type: DEFAULT; Schema: public; Owner: rpg
--

ALTER TABLE ONLY public."character" ALTER COLUMN id SET DEFAULT nextval('public.character_id_seq'::regclass);


--
-- Name: item id; Type: DEFAULT; Schema: public; Owner: rpg
--

ALTER TABLE ONLY public.item ALTER COLUMN id SET DEFAULT nextval('public.item_id_seq'::regclass);


--
-- Data for Name: character; Type: TABLE DATA; Schema: public; Owner: rpg
--

COPY public."character" (id, name) FROM stdin;
1	Asha
2	Brigg
3	Carto
\.


--
-- Data for Name: item; Type: TABLE DATA; Schema: public; Owner: rpg
--

COPY public.item (id, carried_by, description, weight) FROM stdin;
1	1	Cloak	4
2	2	Leather armour	12
3	3	Rags	2
4	3	Short sword	10
5	2	Helm	3
6	2	Axe	18
7	1	Dagger	2
8	1	Health potion	1
9	3	Shield	9
\.


--
-- Name: character_id_seq; Type: SEQUENCE SET; Schema: public; Owner: rpg
--

SELECT pg_catalog.setval('public.character_id_seq', 3, true);


--
-- Name: item_id_seq; Type: SEQUENCE SET; Schema: public; Owner: rpg
--

SELECT pg_catalog.setval('public.item_id_seq', 9, true);


--
-- Name: character character_pkey; Type: CONSTRAINT; Schema: public; Owner: rpg
--

ALTER TABLE ONLY public."character"
    ADD CONSTRAINT character_pkey PRIMARY KEY (id);


--
-- Name: item item_pkey; Type: CONSTRAINT; Schema: public; Owner: rpg
--

ALTER TABLE ONLY public.item
    ADD CONSTRAINT item_pkey PRIMARY KEY (id);


--
-- Name: item item_carried_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rpg
--

ALTER TABLE ONLY public.item
    ADD CONSTRAINT item_carried_by_fkey FOREIGN KEY (carried_by) REFERENCES public."character"(id);


--
-- PostgreSQL database dump complete
--

